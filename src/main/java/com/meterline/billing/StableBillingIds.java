package com.meterline.billing;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

public final class StableBillingIds {

    private StableBillingIds() {
    }

    public static String periodId(Instant startInclusive, Instant endExclusive) {
        return "period_" + hash(startInclusive + "|" + endExclusive);
    }

    public static String aggregateId(String customerId, String meterId, Instant startInclusive, Instant endExclusive) {
        return "agg_" + hash(customerId + "|" + meterId + "|" + startInclusive + "|" + endExclusive);
    }

    public static String invoiceId(String customerId, Instant startInclusive, Instant endExclusive) {
        return "inv_" + hash(customerId + "|" + startInclusive + "|" + endExclusive);
    }

    public static String usageLineId(String invoiceId, String aggregateId) {
        return "line_" + hash(invoiceId + "|usage|" + aggregateId);
    }

    public static String adjustmentLineId(String invoiceId, String eventId) {
        return "line_" + hash(invoiceId + "|adjustment|" + eventId);
    }

    private static String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the JDK", exception);
        }
    }
}
