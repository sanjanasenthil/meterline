package com.meterline.billing;

import com.meterline.events.EventType;
import com.meterline.events.UsageEventRequest;
import com.meterline.events.UsageEventService;
import com.meterline.drilldown.DrillDownRepository;
import com.meterline.drilldown.InvoiceLineDrillDown;
import com.meterline.pricing.BillingPeriod;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class BillingServiceIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("meterline")
            .withUsername("meterline")
            .withPassword("meterline");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    UsageEventService usageEventService;

    @Autowired
    BillingService billingService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    DrillDownRepository drillDownRepository;

    @Test
    void billingRunIsIdempotentAndProducesStableInvoices() {
        BillingPeriod period = january();
        ingestUsage("day3-idempotent-1", 100, period.startInclusive().plus(1, ChronoUnit.HOURS));
        ingestUsage("day3-idempotent-2", 50, period.startInclusive().plus(2, ChronoUnit.HOURS));

        BillingRunResult first = billingService.runBilling(period);
        List<InvoiceView> firstInvoices = billingService.invoicesFor(period);
        BillingRunResult second = billingService.runBilling(period);
        List<InvoiceView> secondInvoices = billingService.invoicesFor(period);

        assertThat(first.invoiceIds()).containsExactlyElementsOf(second.invoiceIds());
        assertThat(firstInvoices).isEqualTo(secondInvoices);
        assertThat(firstInvoices).hasSize(1);
        assertThat(firstInvoices.getFirst().totalCents()).isEqualTo(150);
        assertThat(firstInvoices.getFirst().lines()).hasSize(1);
    }

    @Test
    void rerunAfterPartialInvoiceStateCompletesWithoutDuplicatingLines() {
        BillingPeriod period = new BillingPeriod(
                Instant.parse("2026-03-01T00:00:00Z"),
                Instant.parse("2026-04-01T00:00:00Z"));
        ingestUsage("day3-crash-1", 30, period.startInclusive().plus(1, ChronoUnit.HOURS));

        billingService.runBilling(period);
        jdbcTemplate.update("DELETE FROM invoice_lines WHERE line_type = 'USAGE'");

        billingService.runBilling(period);
        List<InvoiceView> invoices = billingService.invoicesFor(period);

        assertThat(invoices).hasSize(1);
        assertThat(invoices.getFirst().lines()).hasSize(1);
        assertThat(invoices.getFirst().totalCents()).isEqualTo(30);
    }

    @Test
    void lateEventForClosedPeriodAppearsAsAdjustmentInNextPeriod() {
        Instant now = Instant.now();
        BillingPeriod closedPeriod = new BillingPeriod(
                now.minus(3, ChronoUnit.DAYS),
                now.minus(2, ChronoUnit.DAYS));
        BillingPeriod nextPeriod = new BillingPeriod(
                now.minus(1, ChronoUnit.HOURS),
                now.plus(1, ChronoUnit.DAYS));

        billingService.closePeriod(closedPeriod);
        insertLateRawEvent(
                "evt_day3_late",
                "cust-late",
                "api-calls",
                42,
                closedPeriod.startInclusive().plus(1, ChronoUnit.HOURS),
                nextPeriod.startInclusive().plus(10, ChronoUnit.MINUTES));

        billingService.runBilling(nextPeriod);
        List<InvoiceView> invoices = billingService.invoicesFor(nextPeriod);

        assertThat(invoices).hasSize(1);
        assertThat(invoices.getFirst().lines()).singleElement().satisfies(line -> {
            assertThat(line.lineType()).isEqualTo(InvoiceLineType.ADJUSTMENT);
            assertThat(line.adjustmentForEventId()).isEqualTo("evt_day3_late");
            assertThat(line.amountCents()).isEqualTo(42);
            InvoiceLineDrillDown drillDown = drillDownRepository.line(line.lineId())
                    .map(detail -> new InvoiceLineDrillDown(detail, drillDownRepository.eventsFor(detail),
                            drillDownRepository.eventsFor(detail).stream().mapToLong(event -> event.quantityUnits()).sum(),
                            InvoiceLineDrillDown.amountFor(detail.quantityUnits(), detail.rateMillionthsOfCent()),
                            true, true))
                    .orElseThrow();
            assertThat(drillDown.line().adjustment()).isTrue();
            assertThat(drillDown.events()).singleElement().satisfies(event -> {
                assertThat(event.eventId()).isEqualTo("evt_day3_late");
                assertThat(event.quantityUnits()).isEqualTo(42);
            });
            assertThat(drillDown.amountAtAppliedRateCents()).isEqualTo(42);
        });
    }

    @Test
    void invoiceDrillDownReturnsSourceEventsAndAppliedRateProof() {
        BillingPeriod period = january();
        ingestUsage("day5-drill-1", 35, period.startInclusive().plus(1, ChronoUnit.HOURS));
        ingestUsage("day5-drill-2", 65, period.startInclusive().plus(2, ChronoUnit.HOURS));
        billingService.runBilling(period);
        InvoiceView invoice = billingService.invoicesFor(period).getFirst();

        var detail = drillDownRepository.invoice(invoice.invoiceId()).orElseThrow();
        var line = detail.lines().getFirst();
        var events = drillDownRepository.eventsFor(line);

        assertThat(detail.customerId()).isEqualTo("cust-day3");
        assertThat(detail.totalCents()).isEqualTo(100);
        assertThat(detail.status()).isEqualTo("DRAFT");
        assertThat(events).hasSize(2);
        assertThat(events).extracting(event -> event.sourceEventKey())
                .containsExactlyInAnyOrder("day5-drill-1", "day5-drill-2");
        assertThat(events.stream().mapToLong(event -> event.quantityUnits()).sum()).isEqualTo(line.quantityUnits());
        assertThat(InvoiceLineDrillDown.amountFor(line.quantityUnits(), line.rateMillionthsOfCent()))
                .isEqualTo(line.amountCents());
        assertThat(line.pricingModel()).isEqualTo("volume");
        assertThat(line.adjustment()).isFalse();
    }

    private void ingestUsage(String sourceEventKey, long quantity, Instant eventTimestamp) {
        usageEventService.ingest(new UsageEventRequest(
                "cust-day3",
                "api-calls",
                "billing-test",
                sourceEventKey,
                quantity,
                eventTimestamp,
                EventType.USAGE,
                null,
                Map.of()));
    }

    private void insertLateRawEvent(
            String eventId,
            String customerId,
            String meterId,
            long quantity,
            Instant eventTimestamp,
            Instant receivedAt
    ) {
        jdbcTemplate.update("""
                        INSERT INTO raw_usage_events (
                            event_id,
                            customer_id,
                            meter_id,
                            source,
                            source_event_key,
                            quantity_units,
                            event_timestamp,
                            received_at,
                            event_type,
                            metadata
                        )
                        VALUES (?, ?, ?, 'billing-test', ?, ?, ?, ?, 'USAGE', '{}'::jsonb)
                        """,
                eventId,
                customerId,
                meterId,
                eventId,
                quantity,
                Timestamp.from(eventTimestamp),
                Timestamp.from(receivedAt));
    }

    private static BillingPeriod january() {
        return new BillingPeriod(
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-02-01T00:00:00Z"));
    }
}
