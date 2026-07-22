package com.portfolio.auth.exception;

import java.time.Instant;
import java.util.Map;

public record ApiError(Instant timestamp, int status, String error, Object message) {
    public static ApiError of(int status, String error, Object message) {
        return new ApiError(Instant.now(), status, error, message);
    }
}
