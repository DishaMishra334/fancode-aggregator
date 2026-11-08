package com.fancode.aggregator.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
    String error,
    int status,
    Instant timestamp
) {
    public static ErrorResponse of(String message, int status) {
        return new ErrorResponse(message, status, Instant.now());
    }
}
