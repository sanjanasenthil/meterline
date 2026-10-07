package com.meterline.pricing;

import java.time.Instant;

public record RateVersion(Instant effectiveAt, Rate rate) {

    public RateVersion {
        if (effectiveAt == null) {
            throw new IllegalArgumentException("effectiveAt is required");
        }
        if (rate == null) {
            throw new IllegalArgumentException("rate is required");
        }
    }
}
