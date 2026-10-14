package com.cityroutebooking.booking.api;

import java.util.Map;

public record ApiError(int status, String code, String message, Map<String, String> fieldErrors) {
    public ApiError {
        fieldErrors = Map.copyOf(fieldErrors);
    }
}
