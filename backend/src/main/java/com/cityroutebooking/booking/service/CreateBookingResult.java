package com.cityroutebooking.booking.service;

public record CreateBookingResult(VersionedBooking booking, boolean replayed) {
}
