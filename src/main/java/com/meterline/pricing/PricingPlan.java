package com.meterline.pricing;

import java.util.Comparator;
import java.util.List;

public record PricingPlan(
        String planId,
        PricingModel pricingModel,
        List<RateVersion> rateVersions,
        RoundingPolicy roundingPolicy
) {

    public PricingPlan {
        if (planId == null || planId.isBlank()) {
            throw new IllegalArgumentException("planId is required");
        }
        if (pricingModel == null) {
            throw new IllegalArgumentException("pricing model is required");
        }
        if (rateVersions == null || rateVersions.isEmpty()) {
            throw new IllegalArgumentException("at least one rate version is required");
        }
        if (roundingPolicy == null) {
            throw new IllegalArgumentException("rounding policy is required");
        }
        rateVersions = rateVersions.stream()
                .sorted(Comparator.comparing(RateVersion::effectiveAt))
                .toList();
    }
}
