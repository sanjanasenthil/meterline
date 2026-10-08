package com.meterline.events;

import org.springframework.stereotype.Service;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsageEventService {

    private final UsageEventValidator validator;
    private final EventIdGenerator eventIdGenerator;
    private final UsageEventRepository repository;
    private final MeterRegistry meterRegistry;

    public UsageEventService(
            UsageEventValidator validator,
            EventIdGenerator eventIdGenerator,
            UsageEventRepository repository,
            MeterRegistry meterRegistry
    ) {
        this.validator = validator;
        this.eventIdGenerator = eventIdGenerator;
        this.repository = repository;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public IngestUsageEventResponse ingest(UsageEventRequest request) {
        try {
            validator.validate(request);
            String eventId = eventIdGenerator.generate(request);
            boolean inserted = repository.insertIfAbsent(eventId, request);
            meterRegistry.counter(inserted ? "meterline.ingestion.events.inserted" : "meterline.ingestion.events.duplicates").increment();

            return new IngestUsageEventResponse(
                    eventId,
                    inserted,
                    inserted ? "inserted" : "duplicate_ignored");
        } catch (RuntimeException exception) {
            meterRegistry.counter("meterline.ingestion.failures").increment();
            throw exception;
        }
    }
}
