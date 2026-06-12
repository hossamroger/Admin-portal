package com.example.dbexplorer.controller;

import com.example.dbexplorer.dto.ApiError;
import com.example.dbexplorer.security.RequestIdFilter;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final org.slf4j.Logger log =
        org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Build the unified error envelope, attaching the request correlation id from the MDC. */
    private static ApiError error(String message, String type, HttpStatus status) {
        return ApiError.of(message, type, status.value(), MDC.get(RequestIdFilter.MDC_KEY));
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ApiError> handleSecurity(SecurityException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(error(ex.getMessage(), "Forbidden", HttpStatus.FORBIDDEN));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(error(ex.getMessage(), ex.getClass().getSimpleName(), HttpStatus.BAD_REQUEST));
    }

    @ExceptionHandler(java.util.NoSuchElementException.class)
    public ResponseEntity<ApiError> handleNotFound(java.util.NoSuchElementException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(error(ex.getMessage(), "NotFound", HttpStatus.NOT_FOUND));
    }

    // ── Bad request: malformed JSON / wrong param types / missing params ─────────

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(error("Malformed request body", "BadRequest", HttpStatus.BAD_REQUEST));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(error("Invalid value for parameter '" + ex.getName() + "'", "BadRequest", HttpStatus.BAD_REQUEST));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParam(MissingServletRequestParameterException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(error("Missing required parameter '" + ex.getParameterName() + "'", "BadRequest", HttpStatus.BAD_REQUEST));
    }

    // Must not be swallowed by the catch-all: carries its own HTTP status (404/403/…)
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<ApiError> handleStatus(org.springframework.web.server.ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatus())
            .body(error(ex.getReason(), ex.getStatus().getReasonPhrase(), ex.getStatus()));
    }

    // Unexpected failures: log the full detail server-side, but never leak raw
    // exception messages (ORA- codes, table/column names) to the client.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleAll(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(error("Internal server error — please contact an administrator",
                        "InternalError", HttpStatus.INTERNAL_SERVER_ERROR));
    }
}
