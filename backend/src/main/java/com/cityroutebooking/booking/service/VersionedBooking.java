package com.cityroutebooking.booking.service;

import com.cityroutebooking.booking.api.BookingResponse;
import com.cityroutebooking.booking.domain.Booking;

public record VersionedBooking(BookingResponse response, String etag) {

    public static VersionedBooking from(Booking booking) {
        String etag = "\"" + booking.getBookingNumber() + "-v" + booking.getVersion() + "\"";
        return new VersionedBooking(BookingResponse.from(booking), etag);
    }
}
