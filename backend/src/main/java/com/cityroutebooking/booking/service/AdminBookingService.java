package com.cityroutebooking.booking.service;

import com.cityroutebooking.booking.api.AdminBookingPageResponse;
import com.cityroutebooking.booking.api.ApiException;
import com.cityroutebooking.booking.api.BookingResponse;
import com.cityroutebooking.booking.persistence.BookingRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AdminBookingService {
    private static final int MAX_SIZE = 100;

    private final BookingRepository repository;

    public AdminBookingService(BookingRepository repository) {
        this.repository = repository;
    }

    public AdminBookingPageResponse list(int page, int size) {
        if (page < 0) {
            throw invalid("page", "must be zero or greater");
        }
        if (size < 1) {
            throw invalid("size", "must be at least 1");
        }
        if (size > MAX_SIZE) {
            throw invalid("size", "must not exceed 100");
        }

        Sort newestFirst = Sort.by(
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("bookingNumber"));
        PageRequest pageable = PageRequest.of(page, size, newestFirst);
        Page<BookingResponse> responsePage = repository.findAll(pageable).map(BookingResponse::from);
        return AdminBookingPageResponse.from(responsePage);
    }

    private ApiException invalid(String field, String message) {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                "validation_error",
                field + " " + message,
                java.util.Map.of(field, message));
    }
}
