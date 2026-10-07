package com.meterline.pricing;

public record Money(long cents) implements Comparable<Money> {

    public static final Money ZERO = new Money(0);

    public Money plus(Money other) {
        return new Money(Math.addExact(cents, other.cents));
    }

    @Override
    public int compareTo(Money other) {
        return Long.compare(cents, other.cents);
    }
}
