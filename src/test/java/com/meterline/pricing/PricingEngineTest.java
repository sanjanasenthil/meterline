package com.meterline.pricing;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PricingEngineTest {

    private final PricingEngine pricingEngine = new PricingEngine();

    @Test
    void pricesBasicVolumeUsage() {
        BillingPeriod period = january();
        PricingPlan plan = volumePlan(Rate.cents("2"), period.startInclusive());

        PriceResult result = pricingEngine.price(
                List.of(new UsageWindow(period.startInclusive(), period.endExclusive(), 100)),
                plan,
                period);

        assertThat(result.total()).isEqualTo(new Money(200));
        assertThat(result.lines()).hasSize(1);
    }

    @Test
    void pricesMarginalTiersWithoutRepricingEarlierUnits() {
        BillingPeriod period = january();
        PricingPlan plan = new PricingPlan(
                "tiered-api",
                new TieredPricing(List.of(
                        new Tier(100, Rate.cents("1")),
                        new Tier(200, Rate.cents("2")),
                        new Tier(Tier.INFINITY, Rate.cents("5")))),
                List.of(new RateVersion(period.startInclusive(), Rate.cents("0"))),
                RoundingPolicy.HALF_UP_CENTS);

        PriceResult result = pricingEngine.price(
                List.of(new UsageWindow(period.startInclusive(), period.endExclusive(), 250)),
                plan,
                period);

        assertThat(result.total()).isEqualTo(new Money(550));
        assertThat(result.lines()).extracting(PricedLine::quantityUnits)
                .containsExactly(100L, 100L, 50L);
        assertThat(result.lines()).extracting(line -> line.rate().millionthsOfCentPerUnit())
                .containsExactly(
                        Rate.cents("1").millionthsOfCentPerUnit(),
                        Rate.cents("2").millionthsOfCentPerUnit(),
                        Rate.cents("5").millionthsOfCentPerUnit());
    }

    @Test
    void pricesUsageExactlyAtTierBoundary() {
        BillingPeriod period = january();
        PricingPlan plan = tieredBoundaryPlan(period.startInclusive());

        PriceResult result = pricingEngine.price(
                List.of(new UsageWindow(period.startInclusive(), period.endExclusive(), 100)),
                plan,
                period);

        assertThat(result.total()).isEqualTo(new Money(100));
        assertThat(result.lines()).hasSize(1);
    }

    @Test
    void pricesOnlyExtraUnitAtNextTierPastBoundary() {
        BillingPeriod period = january();
        PricingPlan plan = tieredBoundaryPlan(period.startInclusive());

        PriceResult result = pricingEngine.price(
                List.of(new UsageWindow(period.startInclusive(), period.endExclusive(), 101)),
                plan,
                period);

        assertThat(result.total()).isEqualTo(new Money(102));
        assertThat(result.lines()).extracting(PricedLine::quantityUnits)
                .containsExactly(100L, 1L);
    }

    @Test
    void appliesMidPeriodRateChangeAtExactBoundary() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        Instant boundary = Instant.parse("2026-01-14T00:00:00Z");
        Instant end = Instant.parse("2026-01-27T00:00:00Z");
        BillingPeriod period = new BillingPeriod(start, end);
        PricingPlan plan = new PricingPlan(
                "volume-api",
                new VolumePricing(),
                List.of(
                        new RateVersion(start, Rate.cents("1")),
                        new RateVersion(boundary, Rate.cents("3"))),
                RoundingPolicy.HALF_UP_CENTS);

        PriceResult result = pricingEngine.price(
                List.of(new UsageWindow(start, end, 260)),
                plan,
                period);

        assertThat(result.total()).isEqualTo(new Money(520));
        assertThat(result.lines()).hasSize(2);
        assertThat(result.lines().get(0).endExclusive()).isEqualTo(boundary);
        assertThat(result.lines()).extracting(PricedLine::quantityUnits)
                .containsExactly(130L, 130L);
    }

    @Test
    void handlesRateChangeBoundaryExactToTheSecond() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        Instant boundary = Instant.parse("2026-01-01T00:00:10Z");
        Instant end = Instant.parse("2026-01-01T00:00:20Z");
        BillingPeriod period = new BillingPeriod(start, end);
        PricingPlan plan = new PricingPlan(
                "second-boundary",
                new VolumePricing(),
                List.of(
                        new RateVersion(start, Rate.cents("4")),
                        new RateVersion(boundary, Rate.cents("9"))),
                RoundingPolicy.HALF_UP_CENTS);

        PriceResult result = pricingEngine.price(
                List.of(new UsageWindow(start, end, 20)),
                plan,
                period);

        assertThat(result.total()).isEqualTo(new Money(130));
        assertThat(result.lines().get(0).endExclusive()).isEqualTo(boundary);
        assertThat(result.lines().get(1).startInclusive()).isEqualTo(boundary);
    }

    @Test
    void rateSplitDoesNotLoseUnitsWhenQuantityDoesNotDivideEvenly() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        Instant boundary = Instant.parse("2026-01-01T00:00:01Z");
        Instant end = Instant.parse("2026-01-01T00:00:03Z");
        BillingPeriod period = new BillingPeriod(start, end);
        PricingPlan plan = new PricingPlan(
                "non-divisible-rate-split",
                new VolumePricing(),
                List.of(
                        new RateVersion(start, Rate.cents("1")),
                        new RateVersion(boundary, Rate.cents("1"))),
                RoundingPolicy.HALF_UP_CENTS);

        PriceResult result = pricingEngine.price(
                List.of(new UsageWindow(start, end, 10)),
                plan,
                period);

        assertThat(result.lines()).extracting(PricedLine::quantityUnits).containsExactly(3L, 7L);
        assertThat(result.lines().stream().mapToLong(PricedLine::quantityUnits).sum()).isEqualTo(10);
        assertThat(result.total()).isEqualTo(new Money(10));
    }

    @Test
    void roundsFractionalCentsWithDocumentedHalfUpPolicy() {
        BillingPeriod period = january();
        PricingPlan plan = volumePlan(Rate.cents("0.5"), period.startInclusive());

        PriceResult result = pricingEngine.price(
                List.of(new UsageWindow(period.startInclusive(), period.endExclusive(), 1)),
                plan,
                period);

        assertThat(result.total()).isEqualTo(new Money(1));
    }

    @Test
    void tenMillionEventsAtOneTenThousandthDollarHaveNoCentLevelDrift() {
        BillingPeriod period = january();
        PricingPlan plan = volumePlan(Rate.dollars("0.0001"), period.startInclusive());

        PriceResult result = pricingEngine.price(
                List.of(new UsageWindow(period.startInclusive(), period.endExclusive(), 10_000_000)),
                plan,
                period);

        assertThat(result.total()).isEqualTo(new Money(100_000));
    }

    private static PricingPlan volumePlan(Rate rate, Instant effectiveAt) {
        return new PricingPlan(
                "volume-api",
                new VolumePricing(),
                List.of(new RateVersion(effectiveAt, rate)),
                RoundingPolicy.HALF_UP_CENTS);
    }

    private static PricingPlan tieredBoundaryPlan(Instant effectiveAt) {
        return new PricingPlan(
                "tiered-boundary",
                new TieredPricing(List.of(
                        new Tier(100, Rate.cents("1")),
                        new Tier(Tier.INFINITY, Rate.cents("2")))),
                List.of(new RateVersion(effectiveAt, Rate.cents("0"))),
                RoundingPolicy.HALF_UP_CENTS);
    }

    private static BillingPeriod january() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        return new BillingPeriod(start, start.plus(31, ChronoUnit.DAYS));
    }
}
