package com.cityroutebooking.booking.api;

import com.cityroutebooking.booking.service.BookingService;
import com.cityroutebooking.booking.service.CreateBookingResult;
import com.cityroutebooking.booking.service.VersionedBooking;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    public ResponseEntity<BookingResponse> create(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateBookingRequest request) {
        CreateBookingResult result = bookingService.create(idempotencyKey, request);
        VersionedBooking booking = result.booking();
        return ResponseEntity.status(result.replayed() ? 200 : 201)
                .header(HttpHeaders.LOCATION, "/api/bookings/" + booking.response().bookingNumber())
                .header(HttpHeaders.ETAG, booking.etag())
                .body(booking.response());
    }

    @GetMapping("/{bookingNumber}")
    public ResponseEntity<BookingResponse> get(@PathVariable String bookingNumber) {
        VersionedBooking booking = bookingService.get(bookingNumber);
        return ResponseEntity.ok()
                .header(HttpHeaders.ETAG, booking.etag())
                .body(booking.response());
    }

    @PostMapping("/{bookingNumber}/payment")
    public ResponseEntity<BookingResponse> recordPayment(
            @PathVariable String bookingNumber,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody PaymentResultRequest request) {
        VersionedBooking booking = bookingService.recordPayment(bookingNumber, ifMatch, request.result());
        return versioned(booking);
    }

    @PostMapping("/{bookingNumber}/cancellation")
    public ResponseEntity<BookingResponse> cancel(
            @PathVariable String bookingNumber,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        VersionedBooking booking = bookingService.cancel(bookingNumber, ifMatch);
        return versioned(booking);
    }

    private ResponseEntity<BookingResponse> versioned(VersionedBooking booking) {
        return ResponseEntity.ok()
                .header(HttpHeaders.ETAG, booking.etag())
                .body(booking.response());
    }
}
