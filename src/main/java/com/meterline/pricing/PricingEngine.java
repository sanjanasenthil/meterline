package com.meterline.pricing;

import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class PricingEngine {

    public PriceResult price(List<UsageWindow> usageWindows, PricingPlan plan, BillingPeriod period) {
        if (usageWindows == null) {
            throw new IllegalArgumentException("usageWindows is required");
        }
        List<UsageWindow> windows = usageWindows.stream()
                .filter(window -> window.quantityUnits() > 0)
                .sorted(Comparator.comparing(UsageWindow::startInclusive))
                .toList();

        List<PricedLine> lines = switch (plan.pricingModel()) {
            case VolumePricing ignored -> priceVolume(windows, plan, period);
            case TieredPricing tieredPricing -> priceTiered(windows, plan, period, tieredPricing);
        };

        Money total = lines.stream()
                .map(PricedLine::amount)
                .reduce(Money.ZERO, Money::plus);

        return new PriceResult(lines, total);
    }

    private List<PricedLine> priceVolume(List<UsageWindow> windows, PricingPlan plan, BillingPeriod period) {
        List<PricedLine> lines = new ArrayList<>();
        for (UsageWindow window : windows) {
            for (RatedUsageSlice slice : splitByRate(window, plan, period)) {
                Money amount = plan.roundingPolicy()
                        .roundMillionthsOfCent(Math.multiplyExact(slice.quantityUnits(), slice.rate().millionthsOfCentPerUnit()));
                lines.add(new PricedLine(
                        plan.planId(),
                        slice.startInclusive(),
                        slice.endExclusive(),
                        slice.quantityUnits(),
                        slice.rate(),
                        amount));
            }
        }
        return lines;
    }

    private List<PricedLine> priceTiered(
            List<UsageWindow> windows,
            PricingPlan plan,
            BillingPeriod period,
            TieredPricing tieredPricing
    ) {
        List<PricedLine> lines = new ArrayList<>();
        long cumulativeUnitsBeforeSlice = 0;

        for (UsageWindow window : windows) {
            for (RatedUsageSlice slice : splitByRate(window, plan, period)) {
                long remaining = slice.quantityUnits();
                long sliceOffset = 0;

                while (remaining > 0) {
                    Tier tier = tierForUnit(tieredPricing, cumulativeUnitsBeforeSlice + 1);
                    long availableInTier = tier.upToUnitsInclusive() == Tier.INFINITY
                            ? remaining
                            : tier.upToUnitsInclusive() - cumulativeUnitsBeforeSlice;
                    long unitsInTier = Math.min(remaining, availableInTier);
                    UsageWindow timeSlice = proportionalWindow(slice, sliceOffset, unitsInTier);
                    Money amount = plan.roundingPolicy()
                            .roundMillionthsOfCent(Math.multiplyExact(unitsInTier, tier.rate().millionthsOfCentPerUnit()));

                    lines.add(new PricedLine(
                            plan.planId(),
                            timeSlice.startInclusive(),
                            timeSlice.endExclusive(),
                            unitsInTier,
                            tier.rate(),
                            amount));

                    cumulativeUnitsBeforeSlice += unitsInTier;
                    sliceOffset += unitsInTier;
                    remaining -= unitsInTier;
                }
            }
        }
        return lines;
    }

    private List<RatedUsageSlice> splitByRate(UsageWindow window, PricingPlan plan, BillingPeriod period) {
        if (window.startInclusive().isBefore(period.startInclusive()) || window.endExclusive().isAfter(period.endExclusive())) {
            throw new IllegalArgumentException("usage window must fit inside billing period");
        }

        List<RateVersion> versions = plan.rateVersions();
        List<TimeSlice> timeSlices = new ArrayList<>();
        long allocatedQuantity = 0;
        List<RatedUsageSlice> slices = new ArrayList<>();
        Instant cursor = window.startInclusive();

        while (cursor.isBefore(window.endExclusive())) {
            RateVersion active = activeRateAt(versions, cursor);
            Instant nextBoundary = nextRateBoundaryAfter(versions, cursor, window.endExclusive());
            Instant sliceEnd = nextBoundary.isBefore(window.endExclusive()) ? nextBoundary : window.endExclusive();
            timeSlices.add(new TimeSlice(cursor, sliceEnd, active.rate()));
            cursor = sliceEnd;
        }

        for (int index = 0; index < timeSlices.size(); index++) {
            TimeSlice timeSlice = timeSlices.get(index);
            long quantity = index == timeSlices.size() - 1
                    ? window.quantityUnits() - allocatedQuantity
                    : prorateQuantity(window, timeSlice.startInclusive(), timeSlice.endExclusive());
            allocatedQuantity = Math.addExact(allocatedQuantity, quantity);
            if (quantity > 0) {
                slices.add(new RatedUsageSlice(
                        timeSlice.startInclusive(),
                        timeSlice.endExclusive(),
                        quantity,
                        timeSlice.rate()));
            }
        }

        return slices;
    }

    private static RateVersion activeRateAt(List<RateVersion> versions, Instant instant) {
        RateVersion active = versions.getFirst();
        for (RateVersion version : versions) {
            if (!version.effectiveAt().isAfter(instant)) {
                active = version;
            }
        }
        return active;
    }

    private static Instant nextRateBoundaryAfter(List<RateVersion> versions, Instant instant, Instant fallback) {
        return versions.stream()
                .map(RateVersion::effectiveAt)
                .filter(boundary -> boundary.isAfter(instant))
                .min(Instant::compareTo)
                .orElse(fallback);
    }

    private static long prorateQuantity(UsageWindow window, Instant startInclusive, Instant endExclusive) {
        long totalNanos = Duration.between(window.startInclusive(), window.endExclusive()).toNanos();
        long sliceNanos = Duration.between(startInclusive, endExclusive).toNanos();
        BigInteger numerator = BigInteger.valueOf(window.quantityUnits()).multiply(BigInteger.valueOf(sliceNanos));
        return numerator.divide(BigInteger.valueOf(totalNanos)).longValueExact();
    }

    private static UsageWindow proportionalWindow(RatedUsageSlice slice, long unitOffset, long unitCount) {
        long totalNanos = Duration.between(slice.startInclusive(), slice.endExclusive()).toNanos();
        long startOffsetNanos = BigInteger.valueOf(totalNanos)
                .multiply(BigInteger.valueOf(unitOffset))
                .divide(BigInteger.valueOf(slice.quantityUnits()))
                .longValueExact();
        long endOffsetNanos = BigInteger.valueOf(totalNanos)
                .multiply(BigInteger.valueOf(unitOffset + unitCount))
                .divide(BigInteger.valueOf(slice.quantityUnits()))
                .longValueExact();

        return new UsageWindow(
                slice.startInclusive().plusNanos(startOffsetNanos),
                slice.startInclusive().plusNanos(endOffsetNanos),
                unitCount);
    }

    private static Tier tierForUnit(TieredPricing tieredPricing, long oneBasedUnitNumber) {
        return tieredPricing.tiers().stream()
                .filter(tier -> oneBasedUnitNumber <= tier.upToUnitsInclusive())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("tiered pricing must include an infinite final tier"));
    }

    private record RatedUsageSlice(
            Instant startInclusive,
            Instant endExclusive,
            long quantityUnits,
            Rate rate
    ) {
    }

    private record TimeSlice(
            Instant startInclusive,
            Instant endExclusive,
            Rate rate
    ) {
    }
}
