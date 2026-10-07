package com.meterline.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;

public enum RoundingPolicy {
    HALF_UP_CENTS(RoundingMode.HALF_UP);

    private final RoundingMode roundingMode;

    RoundingPolicy(RoundingMode roundingMode) {
        this.roundingMode = roundingMode;
    }

    public Money roundMillionthsOfCent(long millionthsOfCent) {
        BigDecimal cents = BigDecimal.valueOf(millionthsOfCent)
                .divide(BigDecimal.valueOf(Rate.MILLIONTHS_PER_CENT), 0, roundingMode);
        return new Money(cents.longValueExact());
    }
}
