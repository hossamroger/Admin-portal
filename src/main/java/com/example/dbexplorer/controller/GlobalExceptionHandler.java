package com.example.dbexplorer.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, Object>> handleSecurity(SecurityException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", ex.getMessage());
        body.put("type", "Forbidden");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", ex.getMessage());
        body.put("type", ex.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(java.util.NoSuchElementException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(java.util.NoSuchElementException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", ex.getMessage());
        body.put("type", "NotFound");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    // Must not be swallowed by the catch-all: carries its own HTTP status (404/403/…)
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleStatus(org.springframework.web.server.ResponseStatusException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", ex.getReason());
        body.put("type", ex.getStatus().getReasonPhrase());
        return ResponseEntity.status(ex.getStatus()).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleAll(Exception ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", ex.getMessage());
        body.put("type", ex.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
