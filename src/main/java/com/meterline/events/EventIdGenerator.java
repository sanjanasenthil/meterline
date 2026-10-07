package com.meterline.events;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class EventIdGenerator {

    public String generate(UsageEventRequest request) {
        String stableBusinessAction = String.join("|",
                request.customerId(),
                request.meterId(),
                request.source(),
                request.sourceEventKey(),
                request.eventTimestamp().toString(),
                request.eventType().name(),
                nullToEmpty(request.adjustmentForEventId()));

        return "evt_" + sha256(stableBusinessAction);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the JDK", exception);
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
