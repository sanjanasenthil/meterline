package com.meterline.billing;

import com.meterline.pricing.PricingPlan;
import com.meterline.pricing.Rate;
import com.meterline.pricing.RateVersion;
import com.meterline.pricing.RoundingPolicy;
import com.meterline.pricing.VolumePricing;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class BillingPlanCatalog {

    public PricingPlan planFor(String customerId, String meterId, Instant periodStart) {
        return new PricingPlan(
                "default-volume-1-cent",
                new VolumePricing(),
                List.of(new RateVersion(periodStart, Rate.cents("1"))),
                RoundingPolicy.HALF_UP_CENTS);
    }
}
