package com.meterline.events;

import org.springframework.stereotype.Component;

@Component
public class UsageEventValidator {

    public void validate(UsageEventRequest request) {
        if (request.eventType() == EventType.USAGE) {
            validateUsage(request);
        } else if (request.eventType() == EventType.ADJUSTMENT) {
            validateAdjustment(request);
        } else {
            throw new InvalidUsageEventException("eventType must be USAGE or ADJUSTMENT");
        }
    }

    private void validateUsage(UsageEventRequest request) {
        if (request.quantityUnits() <= 0) {
            throw new InvalidUsageEventException("USAGE events must have positive quantityUnits");
        }
        if (request.adjustmentForEventId() != null && !request.adjustmentForEventId().isBlank()) {
            throw new InvalidUsageEventException("USAGE events cannot reference adjustmentForEventId");
        }
    }

    private void validateAdjustment(UsageEventRequest request) {
        if (request.quantityUnits() == 0) {
            throw new InvalidUsageEventException("ADJUSTMENT events must have non-zero quantityUnits");
        }
        if (request.adjustmentForEventId() == null || request.adjustmentForEventId().isBlank()) {
            throw new InvalidUsageEventException("ADJUSTMENT events must reference adjustmentForEventId");
        }
    }
}
