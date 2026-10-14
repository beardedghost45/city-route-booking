package com.cityroutebooking.booking.domain;

import java.util.UUID;

public final class BookingNumber {

    private BookingNumber() {
    }

    public static String generate() {
        return "BK-" + UUID.randomUUID().toString().replace("-", "");
    }
}
