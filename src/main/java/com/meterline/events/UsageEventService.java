package com.meterline.events;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsageEventService {

    private final UsageEventValidator validator;
    private final EventIdGenerator eventIdGenerator;
    private final UsageEventRepository repository;

    public UsageEventService(
            UsageEventValidator validator,
            EventIdGenerator eventIdGenerator,
            UsageEventRepository repository
    ) {
        this.validator = validator;
        this.eventIdGenerator = eventIdGenerator;
        this.repository = repository;
    }

    @Transactional
    public IngestUsageEventResponse ingest(UsageEventRequest request) {
        validator.validate(request);
        String eventId = eventIdGenerator.generate(request);
        boolean inserted = repository.insertIfAbsent(eventId, request);

        return new IngestUsageEventResponse(
                eventId,
                inserted,
                inserted ? "inserted" : "duplicate_ignored");
    }
}
