package com.meterline.kafka;

import com.meterline.billing.BillingService;
import com.meterline.billing.InvoiceView;
import com.meterline.events.EventType;
import com.meterline.events.UsageEventRequest;
import com.meterline.pricing.BillingPeriod;
import com.meterline.reconciliation.ReconciliationReport;
import com.meterline.reconciliation.ReconciliationService;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;
import org.awaitility.Awaitility;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "meterline.kafka.enabled=true",
        "meterline.kafka.topic=meterline-scale-usage-events",
        "spring.kafka.consumer.group-id=meterline-scale-test",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.kafka.listener.concurrency=3",
        "spring.kafka.producer.properties.linger.ms=5",
        "spring.kafka.producer.properties.batch.size=65536"
})
@Testcontainers(disabledWithoutDocker = true)
class KafkaDuplicateReplayScaleTest {

    private static final int UNIQUE_EVENTS = 100_000;
    private static final int CONCURRENT_DUPLICATES = 1_000;
    private static final int CUSTOMER_COUNT = 20;
    private static final BillingPeriod PERIOD = new BillingPeriod(
            Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-09-01T00:00:00Z"));

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("meterline")
            .withUsername("meterline")
            .withPassword("meterline");

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(
            DockerImageName.parse("apache/kafka-native:3.8.0"));

    @DynamicPropertySource
    static void serviceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired
    UsageEventKafkaProducer producer;

    @Autowired
    KafkaTemplate<String, UsageEventRequest> kafkaTemplate;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    MeterRegistry meterRegistry;

    @Autowired
    BillingService billingService;

    @Autowired
    ReconciliationService reconciliationService;

    @Test
    void fullReplayAndConcurrentDuplicatesRemainExactlyOnceForBilling() throws Exception {
        List<UsageEventRequest> events = new ArrayList<>(UNIQUE_EVENTS);
        long expectedTotalUnits = 0;
        for (int i = 0; i < UNIQUE_EVENTS; i++) {
            long quantity = (i % 10) + 1L;
            expectedTotalUnits += quantity;
            events.add(new UsageEventRequest(
                    "scale-customer-" + (i % CUSTOMER_COUNT),
                    "api-calls",
                    "kafka-scale-test",
                    "scale-event-" + i,
                    quantity,
                    PERIOD.startInclusive().plus(i % 2_592_000L, ChronoUnit.SECONDS),
                    EventType.USAGE,
                    null,
                    Map.of("batch", "100k")));
        }

        long ingestionStart = System.nanoTime();
        events.forEach(producer::publish);
        kafkaTemplate.flush();
        events.forEach(producer::publish);
        kafkaTemplate.flush();

        ExecutorService senders = Executors.newFixedThreadPool(4);
        try {
            CompletableFuture<?>[] concurrentSends = IntStream.range(0, 4)
                    .mapToObj(thread -> CompletableFuture.runAsync(() -> {
                        for (int i = thread; i < CONCURRENT_DUPLICATES; i += 4) {
                            UsageEventRequest duplicate = events.get((i * 97) % UNIQUE_EVENTS);
                            kafkaTemplate.send("meterline-scale-usage-events", duplicate.sourceEventKey(), duplicate)
                                    .join();
                        }
                    }, senders))
                    .toArray(CompletableFuture[]::new);
            CompletableFuture.allOf(concurrentSends)
                    .get(3, TimeUnit.MINUTES);
        } finally {
            senders.shutdown();
            assertThat(senders.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }
        double expectedDuplicates = (double) UNIQUE_EVENTS + CONCURRENT_DUPLICATES;
        Awaitility.await().atMost(Duration.ofMinutes(8)).pollInterval(Duration.ofMillis(250))
                .untilAsserted(() -> {
                    assertThat(rawEventCount()).isEqualTo(UNIQUE_EVENTS);
                    assertThat(duplicateCount()).isEqualTo(expectedDuplicates);
                });
        long ingestionMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - ingestionStart);

        long storedUniqueEvents = rawEventCount();
        long duplicatesRejected = (long) duplicateCount();
        assertThat(storedUniqueEvents).isEqualTo(UNIQUE_EVENTS);
        assertThat(duplicatesRejected).isEqualTo((long) UNIQUE_EVENTS + CONCURRENT_DUPLICATES);

        long billingStart = System.nanoTime();
        billingService.runBilling(PERIOD);
        List<InvoiceView> invoices = billingService.invoicesFor(PERIOD);
        long billedTotalCents = invoices.stream().mapToLong(InvoiceView::totalCents).sum();
        long billedUnits = invoices.stream().flatMap(invoice -> invoice.lines().stream())
                .mapToLong(line -> line.quantityUnits()).sum();
        long duplicatesBilled = billedUnits - expectedTotalUnits;
        billingService.issueInvoices(PERIOD);
        long billingMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - billingStart);

        assertThat(invoices).hasSize(CUSTOMER_COUNT);
        assertThat(duplicatesBilled).isZero();
        assertThat(billedTotalCents).isEqualTo(expectedTotalUnits);

        long reconciliationStart = System.nanoTime();
        ReconciliationReport report = reconciliationService.reconcile(PERIOD);
        long reconciliationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - reconciliationStart);
        assertThat(report.matches()).isTrue();
        assertThat(report.differences()).isEmpty();

        System.out.printf(
                "KAFKA_REPLAY_SCALE messages_delivered=%d unique_events_stored=%d " +
                        "duplicates_rejected=%d duplicate_billed=%d expected_total_cents=%d " +
                        "ingestion_ms=%d billing_ms=%d reconciliation_ms=%d%n",
                (2L * UNIQUE_EVENTS) + CONCURRENT_DUPLICATES,
                storedUniqueEvents,
                duplicatesRejected,
                duplicatesBilled,
                expectedTotalUnits,
                ingestionMillis,
                billingMillis,
                reconciliationMillis);
    }

    private long rawEventCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM raw_usage_events", Long.class);
    }

    private double duplicateCount() {
        var counter = meterRegistry.find("meterline.ingestion.events.duplicates").counter();
        return counter == null ? 0 : counter.count();
    }
}
