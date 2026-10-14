package com.cityroutebooking.booking.persistence;

import com.cityroutebooking.booking.domain.Booking;
import com.cityroutebooking.booking.domain.BookingRequestFingerprint;
import com.cityroutebooking.booking.domain.BookingStatus;
import com.cityroutebooking.booking.domain.City;
import com.cityroutebooking.booking.domain.PaymentStatus;
import com.cityroutebooking.booking.domain.RefundStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class BookingRepositoryPostgresTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine");

    @Autowired
    private BookingRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void flywayCreatesSchemaAndRepositoryRoundTripsBookingLifecycleAndVersion() {
        Booking booking = newBooking("idem-round-trip", "Ada Lovelace", "ada@example.test");
        Booking saved = repository.saveAndFlush(booking);
        long initialVersion = saved.getVersion();

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getBookingNumber()).matches("BK-[0-9a-f]{32}");
        assertThat(repository.findByIdempotencyKey("idem-round-trip"))
                .get()
                .extracting(Booking::getRequestFingerprint)
                .isEqualTo(saved.getRequestFingerprint());

        saved.recordPaymentSuccess();
        repository.saveAndFlush(saved);
        entityManager.clear();

        Booking reloaded = repository.findByBookingNumber(saved.getBookingNumber()).orElseThrow();
        assertThat(reloaded.getOrigin()).isEqualTo(City.DELHI);
        assertThat(reloaded.getDestination()).isEqualTo(City.BENGALURU);
        assertThat(reloaded.getTravelDate()).isEqualTo(LocalDate.of(2027, 3, 14));
        assertThat(reloaded.getPassengerCount()).isEqualTo(2);
        assertThat(reloaded.getLeadPassengerName()).isEqualTo("Ada Lovelace");
        assertThat(reloaded.getLeadPassengerContact()).isEqualTo("ada@example.test");
        assertThat(reloaded.getBookingStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(reloaded.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(reloaded.getRefundStatus()).isEqualTo(RefundStatus.NONE);
        assertThat(reloaded.getVersion()).isGreaterThan(initialVersion);
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getUpdatedAt()).isNotNull();

        reloaded.cancel();
        repository.saveAndFlush(reloaded);
        entityManager.clear();
        Booking cancelled = repository.findByBookingNumber(reloaded.getBookingNumber()).orElseThrow();
        assertThat(cancelled.getBookingStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(cancelled.getRefundStatus()).isEqualTo(RefundStatus.PENDING);
    }

    @Test
    void databaseRejectsDuplicateBookingNumbers() {
        Booking saved = repository.saveAndFlush(newBooking("idem-number-a", "Ada", "ada@example.test"));

        assertThatThrownBy(() -> insertDirect(
                saved.getBookingNumber(),
                "idem-number-b",
                City.MUMBAI,
                City.KOLKATA,
                1))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsDuplicateGlobalIdempotencyKeys() {
        repository.saveAndFlush(newBooking("idem-global", "Ada", "ada@example.test"));

        assertThatThrownBy(() -> insertDirect(
                "BK-" + "b".repeat(32),
                "idem-global",
                City.MUMBAI,
                City.KOLKATA,
                1))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsNonPositivePassengerCount() {
        assertThatThrownBy(() -> insertDirect(
                "BK-" + "c".repeat(32),
                "idem-zero",
                City.DELHI,
                City.MUMBAI,
                0))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsSameCityRoute() {
        assertThatThrownBy(() -> insertDirect(
                "BK-" + "d".repeat(32),
                "idem-same-city",
                City.DELHI,
                City.DELHI,
                1))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsUnsupportedCity() {
        assertThatThrownBy(() -> insertDirect(
                "BK-" + "e".repeat(32),
                "idem-unsupported-city",
                "CHENNAI",
                "MUMBAI",
                1,
                "PENDING_PAYMENT",
                "PENDING",
                "NONE"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsUnknownLifecycleStatus() {
        assertThatThrownBy(() -> insertDirect(
                "BK-" + "f".repeat(32),
                "idem-unknown-status",
                "DELHI",
                "MUMBAI",
                1,
                "BOOKED",
                "PENDING",
                "NONE"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Booking newBooking(String idempotencyKey, String name, String contact) {
        return Booking.create(
                City.DELHI,
                City.BENGALURU,
                LocalDate.of(2027, 3, 14),
                2,
                name,
                contact,
                idempotencyKey,
                BookingRequestFingerprint.calculate(
                        City.DELHI,
                        City.BENGALURU,
                        LocalDate.of(2027, 3, 14),
                        2,
                        name,
                        contact));
    }

    private void insertDirect(String bookingNumber, String idempotencyKey, City origin, City destination, int passengers) {
        insertDirect(
                bookingNumber,
                idempotencyKey,
                origin.name(),
                destination.name(),
                passengers,
                "PENDING_PAYMENT",
                "PENDING",
                "NONE");
    }

    private void insertDirect(
            String bookingNumber,
            String idempotencyKey,
            String origin,
            String destination,
            int passengers,
            String bookingStatus,
            String paymentStatus,
            String refundStatus) {
        jdbcTemplate.update("""
                        INSERT INTO bookings (
                            id, booking_number, origin, destination, travel_date, passenger_count,
                            lead_passenger_name, lead_passenger_contact, booking_status, payment_status,
                            refund_status, idempotency_key, request_fingerprint, version, created_at, updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0,
                                  CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """,
                UUID.randomUUID(),
                bookingNumber,
                origin,
                destination,
                LocalDate.of(2027, 3, 14),
                passengers,
                "Test Passenger",
                "passenger@example.test",
                bookingStatus,
                paymentStatus,
                refundStatus,
                idempotencyKey,
                "0".repeat(64));
    }
}
