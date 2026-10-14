package com.cityroutebooking.booking.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreateBookingRequest(
        @NotBlank(message = "origin is required")
        String origin,
        @NotBlank(message = "destination is required")
        String destination,
        @NotNull(message = "travelDate is required")
        LocalDate travelDate,
        @NotNull(message = "passengerCount is required")
        @Positive(message = "passengerCount must be positive")
        Integer passengerCount,
        @NotBlank(message = "leadPassengerName is required")
        @Size(max = 200, message = "leadPassengerName must be at most 200 characters")
        String leadPassengerName,
        @NotBlank(message = "leadPassengerContact is required")
        @Size(max = 254, message = "leadPassengerContact must be at most 254 characters")
        String leadPassengerContact) {
}
