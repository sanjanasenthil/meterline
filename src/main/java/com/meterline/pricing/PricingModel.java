package com.meterline.pricing;

public sealed interface PricingModel permits VolumePricing, TieredPricing {
}
