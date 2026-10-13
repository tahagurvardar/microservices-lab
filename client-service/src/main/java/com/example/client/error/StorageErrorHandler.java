package com.example.client.error;

import feign.FeignException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;

@RestControllerAdvice
public class StorageErrorHandler {

    @ExceptionHandler(FeignException.class)
    public ResponseEntity<ApiError> downstreamFailure(FeignException failure) {
        if (failure.status() >= 400 && failure.status() < 500) {
            HttpStatus status = HttpStatus.resolve(failure.status());
            return ResponseEntity.status(failure.status()).body(new ApiError(
                    OffsetDateTime.now(), failure.status(),
                    status == null ? "Downstream Client Error" : status.getReasonPhrase(),
                    "STORAGE_CLIENT_ERROR", "Storage service rejected the request"));
        }
        return unavailable("STORAGE_SERVICE_UNAVAILABLE",
                "Storage service is temporarily unavailable");
    }

    @ExceptionHandler(CallNotPermittedException.class)
    public ResponseEntity<ApiError> openCircuit(CallNotPermittedException failure) {
        return unavailable("STORAGE_CIRCUIT_OPEN",
                "Storage circuit is open; try again shortly");
    }

    private ResponseEntity<ApiError> unavailable(String code, String message) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ApiError(
                OffsetDateTime.now(), 503, "Service Unavailable", code, message));
    }

    public record ApiError(OffsetDateTime timestamp, int status, String error,
                           String code, String message) {
    }
}
