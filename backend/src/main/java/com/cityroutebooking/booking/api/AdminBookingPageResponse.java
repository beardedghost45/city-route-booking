package com.cityroutebooking.booking.api;

import org.springframework.data.domain.Page;

import java.util.List;

public record AdminBookingPageResponse(
        List<BookingResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public AdminBookingPageResponse {
        items = List.copyOf(items);
    }

    public static AdminBookingPageResponse from(Page<BookingResponse> page) {
        return new AdminBookingPageResponse(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
