package com.cityroutebooking.booking.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookingDomainTest {

    @Test
    void generatesOpaqueUniqueBookingNumbersWithRequiredPrefix() {
        Set<String> numbers = new HashSet<>();
        for (int index = 0; index < 100; index++) {
            numbers.add(BookingNumber.generate());
        }

        assertThat(numbers).hasSize(100).allMatch(value -> value.matches("BK-[0-9a-f]{32}"));
    }

    @Test
    void normalizedCreateFieldsHaveStableFingerprint() {
        String first = BookingRequestFingerprint.calculate(
                City.DELHI,
                City.MUMBAI,
                LocalDate.of(2027, 1, 2),
                2,
                "  Ada Lovelace  ",
                " ada@example.test ");
        String normalized = BookingRequestFingerprint.calculate(
                City.DELHI,
                City.MUMBAI,
                LocalDate.of(2027, 1, 2),
                2,
                "Ada Lovelace",
                "ada@example.test");
        String changed = BookingRequestFingerprint.calculate(
                City.DELHI,
                City.MUMBAI,
                LocalDate.of(2027, 1, 2),
                3,
                "Ada Lovelace",
                "ada@example.test");

        assertThat(first).matches("[0-9a-f]{64}").isEqualTo(normalized).isNotEqualTo(changed);
    }

    @Test
    void rejectsSameCityRouteAndStartsNewBookingPendingPayment() {
        assertThatThrownBy(() -> newBooking(City.DELHI, City.DELHI))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must differ");

        Booking booking = newBooking(City.KOLKATA, City.HYDERABAD);
        assertThat(booking.getBookingStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);
        assertThat(booking.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(booking.getRefundStatus()).isEqualTo(RefundStatus.NONE);
    }

    private Booking newBooking(City origin, City destination) {
        LocalDate travelDate = LocalDate.of(2027, 1, 2);
        return Booking.create(
                origin,
                destination,
                travelDate,
                1,
                "Ada Lovelace",
                "ada@example.test",
                "idem-domain-test",
                BookingRequestFingerprint.calculate(
                        origin,
                        destination,
                        travelDate,
                        1,
                        "Ada Lovelace",
                        "ada@example.test"));
    }
}
