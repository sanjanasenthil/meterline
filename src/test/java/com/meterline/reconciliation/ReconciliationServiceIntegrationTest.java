package com.meterline.reconciliation;

import com.meterline.billing.BillingService;
import com.meterline.events.EventType;
import com.meterline.events.UsageEventRequest;
import com.meterline.events.UsageEventService;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class ReconciliationServiceIntegrationTest {

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
    ReconciliationService reconciliationService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void correctIssuedInvoiceReconcilesToRawEvents() {
        BillingPeriod period = period("2026-04-01T00:00:00Z", "2026-05-01T00:00:00Z");
        ingest("day4-ok-1", 40, period.startInclusive().plus(1, ChronoUnit.HOURS));
        ingest("day4-ok-2", 60, period.startInclusive().plus(2, ChronoUnit.HOURS));

        billingService.runBilling(period);
        billingService.issueInvoices(period);

        ReconciliationReport report = reconciliationService.reconcile(period);

        assertThat(report.matches()).isTrue();
        assertThat(report.expectedTotalCents()).isEqualTo(100);
        assertThat(report.actualTotalCents()).isEqualTo(100);
    }

    @Test
    void oneCentCorruptionIsDetectedAndQuantified() {
        BillingPeriod period = period("2026-05-01T00:00:00Z", "2026-06-01T00:00:00Z");
        ingest("day4-corrupt-1", 100, period.startInclusive().plus(1, ChronoUnit.HOURS));

        billingService.runBilling(period);
        billingService.issueInvoices(period);
        billingService.corruptFirstInvoiceLineForTest(period, 1);

        ReconciliationReport report = reconciliationService.reconcile(period);

        assertThat(report.matches()).isFalse();
        assertThat(report.differences())
                .anySatisfy(difference -> {
                    assertThat(difference.type()).isEqualTo(ReconciliationMismatchType.LINE_AMOUNT_MISMATCH);
                    assertThat(difference.expectedAmountCents()).isEqualTo(100);
                    assertThat(difference.actualAmountCents()).isEqualTo(101);
                    assertThat(difference.differenceCents()).isEqualTo(1);
                });
    }

    @Test
    void reconciliationUsesRawEventsRatherThanStoredAggregates() {
        BillingPeriod period = period("2026-06-01T00:00:00Z", "2026-07-01T00:00:00Z");
        ingest("day4-raw-source-1", 25, period.startInclusive().plus(1, ChronoUnit.HOURS));

        billingService.runBilling(period);
        billingService.issueInvoices(period);
        jdbcTemplate.update("""
                UPDATE usage_aggregates
                SET quantity_units = 999999
                WHERE period_start = ?
                  AND period_end = ?
                """, Timestamp.from(period.startInclusive()), Timestamp.from(period.endExclusive()));

        ReconciliationReport report = reconciliationService.reconcile(period);

        assertThat(report.matches()).isTrue();
        assertThat(report.expectedTotalCents()).isEqualTo(25);
    }

    private void ingest(String sourceEventKey, long quantity, Instant eventTimestamp) {
        usageEventService.ingest(new UsageEventRequest(
                "cust-day4",
                "api-calls",
                "reconciliation-test",
                sourceEventKey,
                quantity,
                eventTimestamp,
                EventType.USAGE,
                null,
                Map.of()));
    }

    private static BillingPeriod period(String start, String end) {
        return new BillingPeriod(Instant.parse(start), Instant.parse(end));
    }
}
