package com.meterline.pricing;

import java.util.List;

public record PriceResult(List<PricedLine> lines, Money total) {

    public PriceResult {
        lines = List.copyOf(lines);
    }
}
