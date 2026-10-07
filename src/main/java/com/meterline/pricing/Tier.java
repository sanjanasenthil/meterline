package com.meterline.pricing;

public record Tier(long upToUnitsInclusive, Rate rate) {

    public static final long INFINITY = Long.MAX_VALUE;

    public Tier {
        if (upToUnitsInclusive <= 0) {
            throw new IllegalArgumentException("tier upper bound must be positive");
        }
        if (rate == null) {
            throw new IllegalArgumentException("tier rate is required");
        }
    }
}
