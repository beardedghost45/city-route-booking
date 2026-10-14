package com.cityroutebooking.booking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

@Entity
@Table(name = "bookings", uniqueConstraints = {
        @UniqueConstraint(name = "uk_bookings_booking_number", columnNames = "booking_number"),
        @UniqueConstraint(name = "uk_bookings_idempotency_key", columnNames = "idempotency_key")
})
public class Booking {

    private static final Pattern FINGERPRINT_PATTERN = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern BOOKING_NUMBER_PATTERN = Pattern.compile("BK-[0-9a-f]{32}");

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "booking_number", nullable = false, length = 35, updatable = false)
    private String bookingNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin", nullable = false, length = 16, updatable = false)
    private City origin;

    @Enumerated(EnumType.STRING)
    @Column(name = "destination", nullable = false, length = 16, updatable = false)
    private City destination;

    @Column(name = "travel_date", nullable = false, updatable = false)
    private LocalDate travelDate;

    @Column(name = "passenger_count", nullable = false, updatable = false)
    private int passengerCount;

    @Column(name = "lead_passenger_name", nullable = false, length = 200, updatable = false)
    private String leadPassengerName;

    @Column(name = "lead_passenger_contact", nullable = false, length = 254, updatable = false)
    private String leadPassengerContact;

    @Enumerated(EnumType.STRING)
    @Column(name = "booking_status", nullable = false, length = 24)
    private BookingStatus bookingStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 16)
    private PaymentStatus paymentStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "refund_status", nullable = false, length = 16)
    private RefundStatus refundStatus;

    @Column(name = "idempotency_key", nullable = false, length = 255, updatable = false)
    private String idempotencyKey;

    @Column(name = "request_fingerprint", nullable = false, length = 64, updatable = false)
    private String requestFingerprint;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Booking() {
        // Required by JPA.
    }

    public static Booking create(
            City origin,
            City destination,
            LocalDate travelDate,
            int passengerCount,
            String leadPassengerName,
            String leadPassengerContact,
            String idempotencyKey,
            String requestFingerprint) {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(travelDate, "travelDate");
        if (origin == destination) {
            throw new IllegalArgumentException("Origin and destination must differ");
        }
        if (passengerCount < 1) {
            throw new IllegalArgumentException("Passenger count must be positive");
        }

        String normalizedName = requiredTrimmed(leadPassengerName, "leadPassengerName");
        String normalizedContact = requiredTrimmed(leadPassengerContact, "leadPassengerContact");
        String normalizedKey = requiredTrimmed(idempotencyKey, "idempotencyKey");
        String normalizedFingerprint = Objects.requireNonNull(requestFingerprint, "requestFingerprint")
                .toLowerCase(Locale.ROOT);
        if (!FINGERPRINT_PATTERN.matcher(normalizedFingerprint).matches()) {
            throw new IllegalArgumentException("Request fingerprint must be a lowercase SHA-256 hex digest");
        }
        if (normalizedName.length() > 200) {
            throw new IllegalArgumentException("Lead passenger name must be at most 200 characters");
        }
        if (normalizedContact.length() > 254) {
            throw new IllegalArgumentException("Lead passenger contact must be at most 254 characters");
        }
        if (normalizedKey.length() > 255) {
            throw new IllegalArgumentException("Idempotency key must be at most 255 characters");
        }

        Booking booking = new Booking();
        booking.bookingNumber = BookingNumber.generate();
        if (!BOOKING_NUMBER_PATTERN.matcher(booking.bookingNumber).matches()) {
            throw new IllegalStateException("Generated booking number has an invalid format");
        }
        booking.origin = origin;
        booking.destination = destination;
        booking.travelDate = travelDate;
        booking.passengerCount = passengerCount;
        booking.leadPassengerName = normalizedName;
        booking.leadPassengerContact = normalizedContact;
        booking.bookingStatus = BookingStatus.PENDING_PAYMENT;
        booking.paymentStatus = PaymentStatus.PENDING;
        booking.refundStatus = RefundStatus.NONE;
        booking.idempotencyKey = normalizedKey;
        booking.requestFingerprint = normalizedFingerprint;
        return booking;
    }

    public void recordPaymentSuccess() {
        if (bookingStatus != BookingStatus.PENDING_PAYMENT) {
            throw new IllegalStateException("Only a pending booking can be paid");
        }
        bookingStatus = BookingStatus.CONFIRMED;
        paymentStatus = PaymentStatus.SUCCEEDED;
    }

    public void recordPaymentFailure() {
        if (bookingStatus != BookingStatus.PENDING_PAYMENT) {
            throw new IllegalStateException("Only a pending booking can record payment failure");
        }
        paymentStatus = PaymentStatus.FAILED;
    }

    public void cancel() {
        if (bookingStatus == BookingStatus.CANCELLED) {
            throw new IllegalStateException("Booking is already cancelled");
        }
        bookingStatus = BookingStatus.CANCELLED;
        refundStatus = paymentStatus == PaymentStatus.SUCCEEDED ? RefundStatus.PENDING : RefundStatus.NONE;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getBookingNumber() {
        return bookingNumber;
    }

    public City getOrigin() {
        return origin;
    }

    public City getDestination() {
        return destination;
    }

    public LocalDate getTravelDate() {
        return travelDate;
    }

    public int getPassengerCount() {
        return passengerCount;
    }

    public String getLeadPassengerName() {
        return leadPassengerName;
    }

    public String getLeadPassengerContact() {
        return leadPassengerContact;
    }

    public BookingStatus getBookingStatus() {
        return bookingStatus;
    }

    public PaymentStatus getPaymentStatus() {
        return paymentStatus;
    }

    public RefundStatus getRefundStatus() {
        return refundStatus;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestFingerprint() {
        return requestFingerprint;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
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
