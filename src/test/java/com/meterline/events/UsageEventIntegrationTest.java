package com.meterline.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meterline.synthetic.SyntheticBatch;
import com.meterline.synthetic.SyntheticEventGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class UsageEventIntegrationTest {

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
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UsageEventService usageEventService;

    @Autowired
    UsageEventRepository usageEventRepository;

    @Autowired
    EventIdGenerator eventIdGenerator;

    @Autowired
    SyntheticEventGenerator syntheticEventGenerator;

    @Test
    void storesAValidEventThroughTheApi() throws Exception {
        UsageEventRequest request = usageRequest("api-valid-event", 25);

        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.inserted").value(true))
                .andExpect(jsonPath("$.status").value("inserted"));

        String eventId = eventIdGenerator.generate(request);
        assertThat(usageEventRepository.findById(eventId)).isPresent();
    }

    @Test
    void rejectsMalformedEvents() throws Exception {
        Map<String, Object> malformed = Map.of(
                "customerId", "cust-1",
                "meterId", "api-calls",
                "source", "api-test");

        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(malformed)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void storesDuplicateDeliveryOnlyOnce() {
        UsageEventRequest request = usageRequest("duplicate-event", 10);

        IngestUsageEventResponse first = usageEventService.ingest(request);
        IngestUsageEventResponse second = usageEventService.ingest(request);
        IngestUsageEventResponse third = usageEventService.ingest(request);

        assertThat(first.inserted()).isTrue();
        assertThat(second.inserted()).isFalse();
        assertThat(third.inserted()).isFalse();

        EventTotals totals = usageEventRepository.totals(
                request.customerId(),
                request.meterId(),
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-02T00:00:00Z"));

        assertThat(totals.eventCount()).isEqualTo(1);
        assertThat(totals.quantityUnits()).isEqualTo(10);
    }

    @Test
    void concurrentDuplicateDeliveryIsProtectedByTheDatabaseConstraint() throws Exception {
        UsageEventRequest request = usageRequest("concurrent-duplicate-event", 17);
        List<Callable<IngestUsageEventResponse>> tasks = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            tasks.add(() -> usageEventService.ingest(request));
        }

        try (var executor = Executors.newFixedThreadPool(6)) {
            List<IngestUsageEventResponse> responses = executor.invokeAll(tasks).stream()
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (Exception exception) {
                            throw new AssertionError(exception);
                        }
                    })
                    .toList();

            assertThat(responses).filteredOn(IngestUsageEventResponse::inserted).hasSize(1);
        }

        EventTotals totals = usageEventRepository.totals(
                request.customerId(),
                request.meterId(),
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-02T00:00:00Z"));

        assertThat(totals.eventCount()).isEqualTo(1);
        assertThat(totals.quantityUnits()).isEqualTo(17);
    }

    @Test
    void sevenDayBackfillReplayDoesNotMoveTotals() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        SyntheticBatch batch = syntheticEventGenerator.sevenDayBackfillBatch(start, 2, 3);

        syntheticEventGenerator.duplicateReplay(batch, 3)
                .forEach(usageEventService::ingest);

        EventTotals customerOneTotals = usageEventRepository.totals(
                "cust-1",
                "api-calls",
                start,
                start.plusSeconds(7 * 24 * 60 * 60L));

        assertThat(customerOneTotals.eventCount()).isEqualTo(21);
        assertThat(customerOneTotals.quantityUnits())
                .isEqualTo(batch.expectedUnitsByCustomerAndMeter().get("cust-1|api-calls"));
    }

    @Test
    void correctionIsStoredAsAnAdjustmentRecordInsteadOfReplacingOriginalUsage() {
        UsageEventRequest original = usageRequest("event-to-adjust", 100);
        IngestUsageEventResponse originalResponse = usageEventService.ingest(original);

        UsageEventRequest adjustment = new UsageEventRequest(
                original.customerId(),
                original.meterId(),
                "unit-test",
                "event-to-adjust-correction",
                -25,
                Instant.parse("2026-01-01T00:05:00Z"),
                EventType.ADJUSTMENT,
                originalResponse.eventId(),
                Map.of("reason", "customer-credit"));

        IngestUsageEventResponse adjustmentResponse = usageEventService.ingest(adjustment);

        assertThat(adjustmentResponse.inserted()).isTrue();
        assertThat(usageEventRepository.findById(originalResponse.eventId()))
                .get()
                .extracting(UsageEventRow::quantityUnits)
                .isEqualTo(100L);
        assertThat(usageEventRepository.findById(adjustmentResponse.eventId()))
                .get()
                .satisfies(row -> {
                    assertThat(row.eventType()).isEqualTo(EventType.ADJUSTMENT);
                    assertThat(row.quantityUnits()).isEqualTo(-25);
                    assertThat(row.adjustmentForEventId()).isEqualTo(originalResponse.eventId());
                });
    }

    private static UsageEventRequest usageRequest(String sourceEventKey, long quantityUnits) {
        return new UsageEventRequest(
                "cust-test-" + sourceEventKey,
                "api-calls",
                "unit-test",
                sourceEventKey,
                quantityUnits,
                Instant.parse("2026-01-01T00:00:00Z"),
                EventType.USAGE,
                null,
                Map.of("test", true));
    }
}
