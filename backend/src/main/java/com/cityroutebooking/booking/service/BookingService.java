package com.cityroutebooking.booking.service;

import com.cityroutebooking.booking.api.ApiException;
import com.cityroutebooking.booking.api.CreateBookingRequest;
import com.cityroutebooking.booking.domain.Booking;
import com.cityroutebooking.booking.domain.BookingRequestFingerprint;
import com.cityroutebooking.booking.domain.City;
import com.cityroutebooking.booking.persistence.BookingRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;

@Service
public class BookingService {

    private static final Pattern ETAG_PATTERN = Pattern.compile("\\\"BK-[0-9a-f]{32}-v(?:0|[1-9][0-9]*)\\\"");

    private final BookingRepository repository;
    private final TransactionTemplate transactions;

    public BookingService(BookingRepository repository, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public CreateBookingResult create(String rawIdempotencyKey, CreateBookingRequest request) {
        String idempotencyKey = normalizeIdempotencyKey(rawIdempotencyKey);
        City origin = parseCity(request.origin(), "origin");
        City destination = parseCity(request.destination(), "destination");
        LocalDate travelDate = request.travelDate();
        int passengerCount = request.passengerCount();
        String name = request.leadPassengerName().strip();
        String contact = request.leadPassengerContact().strip();
        if (origin == destination) {
            throw invalid("origin and destination must differ", "destination");
        }
        String fingerprint = BookingRequestFingerprint.calculate(
                origin, destination, travelDate, passengerCount, name, contact);

        try {
            return inTransaction(() -> {
                Optional<Booking> existing = repository.findByIdempotencyKey(idempotencyKey);
                if (existing.isPresent()) {
                    return replay(existing.get(), fingerprint);
                }
                Booking created = Booking.create(
                        origin,
                        destination,
                        travelDate,
                        passengerCount,
                        name,
                        contact,
                        idempotencyKey,
                        fingerprint);
                Booking saved = repository.saveAndFlush(created);
                return new CreateBookingResult(VersionedBooking.from(saved), false);
            });
        } catch (DataIntegrityViolationException collision) {
            // A simultaneous request may have inserted the same globally unique key after our lookup.
            // Resolve it in a fresh transaction after the losing insert has rolled back.
            return inTransaction(() -> repository.findByIdempotencyKey(idempotencyKey)
                    .map(existing -> replay(existing, fingerprint))
                    .orElseThrow(() -> collision));
        }
    }

    public VersionedBooking get(String bookingNumber) {
        return inTransaction(() -> VersionedBooking.from(findByBookingNumber(bookingNumber)));
    }

    public VersionedBooking recordPayment(String bookingNumber, String ifMatch, String rawResult) {
        requireIfMatch(ifMatch);
        String result = rawResult == null ? "" : rawResult.strip().toUpperCase(Locale.ROOT);
        if (!result.equals("SUCCESS") && !result.equals("FAILURE")) {
            throw invalid("result must be SUCCESS or FAILURE", "result");
        }
        return inTransaction(() -> mutate(bookingNumber, ifMatch, booking -> {
            if (result.equals("SUCCESS")) {
                booking.recordPaymentSuccess();
            } else {
                booking.recordPaymentFailure();
            }
        }));
    }

    public VersionedBooking cancel(String bookingNumber, String ifMatch) {
        requireIfMatch(ifMatch);
        return inTransaction(() -> mutate(bookingNumber, ifMatch, Booking::cancel));
    }

    private VersionedBooking mutate(String bookingNumber, String ifMatch, BookingMutation mutation) {
        Booking booking = findByBookingNumber(bookingNumber);
        requireCurrentTag(booking, ifMatch);
        try {
            mutation.apply(booking);
        } catch (IllegalStateException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "invalid_state_transition", exception.getMessage());
        }
        return VersionedBooking.from(repository.saveAndFlush(booking));
    }

    private Booking findByBookingNumber(String bookingNumber) {
        return repository.findByBookingNumber(bookingNumber)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "booking_not_found", "Booking was not found"));
    }

    private CreateBookingResult replay(Booking existing, String fingerprint) {
        if (!existing.getRequestFingerprint().equals(fingerprint)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "idempotency_conflict",
                    "Idempotency-Key was already used with a different booking request");
        }
        return new CreateBookingResult(VersionedBooking.from(existing), true);
    }

    private void requireIfMatch(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new ApiException(
                    HttpStatus.PRECONDITION_REQUIRED,
                    "precondition_required",
                    "If-Match is required for booking changes");
        }
        if (!ETAG_PATTERN.matcher(ifMatch.strip()).matches()) {
            throw invalid("If-Match must contain one strong ETag returned by this API", "If-Match");
        }
    }

    private void requireCurrentTag(Booking booking, String ifMatch) {
        String supplied = ifMatch.strip();
        String current = VersionedBooking.from(booking).etag();
        if (!current.equals(supplied)) {
            throw new ApiException(
                    HttpStatus.PRECONDITION_FAILED,
                    "precondition_failed",
                    "The booking changed after the supplied If-Match tag was issued");
        }
    }

    private String normalizeIdempotencyKey(String key) {
        if (key == null || key.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "validation_error", "Idempotency-Key is required",
                    Map.of("Idempotency-Key", "must not be blank"));
        }
        String normalized = key.strip();
        if (normalized.length() > 255) {
            throw invalid("Idempotency-Key must be at most 255 characters", "Idempotency-Key");
        }
        return normalized;
    }

    private City parseCity(String value, String field) {
        if (value == null) {
            throw invalid(field + " is required", field);
        }
        try {
            return City.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw invalid(field + " must be one of Delhi, Mumbai, Hyderabad, Kolkata, or Bengaluru", field);
        }
    }

    private ApiException invalid(String message, String field) {
        return new ApiException(HttpStatus.BAD_REQUEST, "validation_error", message, Map.of(field, message));
    }

    private <T> T inTransaction(Supplier<T> callback) {
        return Objects.requireNonNull(transactions.execute(status -> callback.get()));
    }

    @FunctionalInterface
    private interface BookingMutation {
        void apply(Booking booking);
    }
}
