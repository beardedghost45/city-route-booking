package com.cityroutebooking.booking.domain;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Stable digest of the normalized create-request fields stored with an idempotency key.
 * The field order and encoding are versioned because retries can outlive application releases.
 */
public final class BookingRequestFingerprint {

    private static final byte[] FORMAT_VERSION = "city-route-booking:create:v1".getBytes(StandardCharsets.UTF_8);

    private BookingRequestFingerprint() {
    }

    public static String calculate(
            City origin,
            City destination,
            LocalDate travelDate,
            int passengerCount,
            String leadPassengerName,
            String leadPassengerContact) {
        List<String> values = List.of(
                Objects.requireNonNull(origin, "origin").name(),
                Objects.requireNonNull(destination, "destination").name(),
                Objects.requireNonNull(travelDate, "travelDate").toString(),
                Integer.toString(passengerCount),
                requiredTrimmed(leadPassengerName, "leadPassengerName"),
                requiredTrimmed(leadPassengerContact, "leadPassengerContact"));

        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                writeField(output, FORMAT_VERSION);
                for (String value : values) {
                    writeField(output, value.getBytes(StandardCharsets.UTF_8));
                }
            }
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray());
            return HexFormat.of().formatHex(digest);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to calculate booking request fingerprint", exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static void writeField(DataOutputStream output, byte[] value) throws IOException {
        output.writeInt(value.length);
        output.write(value);
    }

    private static String requiredTrimmed(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
