package com.meterline.pricing;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PricingEnginePropertiesTest {

    private final PricingEngine pricingEngine = new PricingEngine();
    private final BillingPeriod period = new BillingPeriod(
            Instant.parse("2026-01-01T00:00:00Z"),
            Instant.parse("2026-01-02T00:00:00Z"));

    @Property
    void volumePricingIsMonotonic(
            @ForAll @IntRange(min = 0, max = 50_000) int smallerUsage,
            @ForAll @IntRange(min = 0, max = 50_000) int extraUsage
    ) {
        PricingPlan plan = volumePlan(Rate.cents("3"));

        Money smaller = price(plan, smallerUsage);
        Money larger = price(plan, smallerUsage + extraUsage);

        assertThat(larger).isGreaterThanOrEqualTo(smaller);
    }

    @Property
    void tierBoundaryIsContinuousAroundTheCrossing(
            @ForAll @IntRange(min = 1, max = 10_000) int firstTierUnits
    ) {
        PricingPlan plan = new PricingPlan(
                "tier-continuity",
                new TieredPricing(List.of(
                        new Tier(firstTierUnits, Rate.cents("2")),
                        new Tier(Tier.INFINITY, Rate.cents("5")))),
                List.of(new RateVersion(period.startInclusive(), Rate.cents("0"))),
                RoundingPolicy.HALF_UP_CENTS);

        Money atBoundary = price(plan, firstTierUnits);
        Money onePastBoundary = price(plan, firstTierUnits + 1);

        assertThat(onePastBoundary.cents() - atBoundary.cents()).isEqualTo(5);
    }

    @Property
    void splittingPeriodAndSummingMatchesWholePeriodForIntegralCentRates(
            @ForAll @IntRange(min = 0, max = 50_000) int usage
    ) {
        PricingPlan plan = volumePlan(Rate.cents("7"));
        Instant midpoint = period.startInclusive().plus(12, ChronoUnit.HOURS);

        Money whole = pricingEngine.price(
                List.of(new UsageWindow(period.startInclusive(), period.endExclusive(), usage)),
                plan,
                period).total();
        Money split = pricingEngine.price(
                List.of(
                        new UsageWindow(period.startInclusive(), midpoint, usage / 2),
                        new UsageWindow(midpoint, period.endExclusive(), usage - usage / 2)),
                plan,
                period).total();

        assertThat(split).isEqualTo(whole);
    }

    @Property
    void pricingIsDeterministic(@ForAll @IntRange(min = 0, max = 50_000) int usage) {
        PricingPlan plan = volumePlan(Rate.cents("11"));
        List<UsageWindow> usageWindows = List.of(new UsageWindow(
                period.startInclusive(),
                period.endExclusive(),
                usage));

        PriceResult first = pricingEngine.price(usageWindows, plan, period);
        PriceResult second = pricingEngine.price(usageWindows, plan, period);

        assertThat(second).isEqualTo(first);
    }

    @Test
    void pricingEngineDoesNotDependOnSpringOrPersistenceTypes() {
        assertThat(PricingEngine.class.getDeclaredFields()).isEmpty();
    }

    private Money price(PricingPlan plan, long usage) {
        return pricingEngine.price(
                List.of(new UsageWindow(period.startInclusive(), period.endExclusive(), usage)),
                plan,
                period).total();
    }

    private static PricingPlan volumePlan(Rate rate) {
        return new PricingPlan(
                "volume-property",
                new VolumePricing(),
                List.of(new RateVersion(Instant.parse("2026-01-01T00:00:00Z"), rate)),
                RoundingPolicy.HALF_UP_CENTS);
    }
}
