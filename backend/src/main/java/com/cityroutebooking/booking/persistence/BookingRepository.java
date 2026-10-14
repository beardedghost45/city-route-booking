package com.cityroutebooking.booking.persistence;

import com.cityroutebooking.booking.domain.Booking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    Optional<Booking> findByBookingNumber(String bookingNumber);

    Optional<Booking> findByIdempotencyKey(String idempotencyKey);
}
