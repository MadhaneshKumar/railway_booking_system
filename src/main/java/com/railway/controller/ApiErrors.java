package com.railway.controller;

import com.railway.service.BookingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/** JSON errors for the REST controllers (the SOAP endpoint has its own fault mapping). */
@RestControllerAdvice(assignableTypes = {AuthController.class, TicketDownloadController.class})
public class ApiErrors {
    private static final Logger log = LoggerFactory.getLogger(ApiErrors.class);

    static HttpStatus statusFor(String code) {
        if (code == null) return HttpStatus.BAD_REQUEST;
        return switch (code) {
            case "UNAUTHENTICATED", "INVALID_CREDENTIALS" -> HttpStatus.UNAUTHORIZED;
            case "USERNAME_TAKEN" -> HttpStatus.CONFLICT;
            case "RATE_LIMITED" -> HttpStatus.TOO_MANY_REQUESTS;
            case "PNR_NOT_FOUND", "ORDER_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            default -> HttpStatus.BAD_REQUEST;
        };
    }

    @ExceptionHandler(BookingException.class)
    ResponseEntity<Map<String, String>> booking(BookingException e) {
        return ResponseEntity.status(statusFor(e.code)).body(Map.of("code", e.code, "message", e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, String>> other(Exception e) {
        log.error("Unexpected error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("code", "INTERNAL_ERROR", "message", "Something went wrong"));
    }
}
