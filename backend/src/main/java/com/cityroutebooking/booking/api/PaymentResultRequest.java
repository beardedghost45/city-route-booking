package com.cityroutebooking.booking.api;

import jakarta.validation.constraints.NotBlank;

public record PaymentResultRequest(@NotBlank(message = "result is required") String result) {
}
