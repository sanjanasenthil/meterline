package com.meterline.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record Rate(long millionthsOfCentPerUnit) {

    public static final long MILLIONTHS_PER_CENT = 1_000_000L;

    public Rate {
        if (millionthsOfCentPerUnit < 0) {
            throw new IllegalArgumentException("rate cannot be negative");
        }
    }

    public static Rate cents(String centsPerUnit) {
        BigDecimal scaled = new BigDecimal(centsPerUnit)
                .multiply(BigDecimal.valueOf(MILLIONTHS_PER_CENT))
                .setScale(0, RoundingMode.UNNECESSARY);
        return new Rate(scaled.longValueExact());
    }

    public static Rate dollars(String dollarsPerUnit) {
        BigDecimal scaled = new BigDecimal(dollarsPerUnit)
                .multiply(BigDecimal.valueOf(100L * MILLIONTHS_PER_CENT))
                .setScale(0, RoundingMode.UNNECESSARY);
        return new Rate(scaled.longValueExact());
    }
}
