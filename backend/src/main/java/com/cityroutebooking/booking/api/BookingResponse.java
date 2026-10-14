package com.cityroutebooking.booking.api;

import com.cityroutebooking.booking.domain.Booking;
import com.cityroutebooking.booking.domain.BookingStatus;
import com.cityroutebooking.booking.domain.PaymentStatus;
import com.cityroutebooking.booking.domain.RefundStatus;

import java.time.Instant;
import java.time.LocalDate;

public record BookingResponse(
        String bookingNumber,
        String origin,
        String destination,
        LocalDate travelDate,
        int passengerCount,
        String leadPassengerName,
        String leadPassengerContact,
        BookingStatus bookingStatus,
        PaymentStatus paymentStatus,
        RefundStatus refundStatus,
        Instant createdAt,
        Instant updatedAt) {

    public static BookingResponse from(Booking booking) {
        return new BookingResponse(
                booking.getBookingNumber(),
                booking.getOrigin().name(),
                booking.getDestination().name(),
                booking.getTravelDate(),
                booking.getPassengerCount(),
                booking.getLeadPassengerName(),
                booking.getLeadPassengerContact(),
                booking.getBookingStatus(),
                booking.getPaymentStatus(),
                booking.getRefundStatus(),
                booking.getCreatedAt(),
                booking.getUpdatedAt());
    }
}
