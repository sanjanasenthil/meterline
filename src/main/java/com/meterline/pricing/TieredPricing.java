package com.meterline.pricing;

import java.util.List;

public record TieredPricing(List<Tier> tiers) implements PricingModel {

    public TieredPricing {
        if (tiers == null || tiers.isEmpty()) {
            throw new IllegalArgumentException("at least one tier is required");
        }
        long previous = 0;
        for (Tier tier : tiers) {
            if (tier.upToUnitsInclusive() <= previous) {
                throw new IllegalArgumentException("tiers must be strictly increasing");
            }
            previous = tier.upToUnitsInclusive();
        }
        tiers = List.copyOf(tiers);
    }
}
