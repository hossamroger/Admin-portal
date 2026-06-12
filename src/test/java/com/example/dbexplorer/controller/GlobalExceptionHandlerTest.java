package com.example.dbexplorer.controller;

import com.example.dbexplorer.dto.ApiError;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the new bad-request exception handlers map to 400 and emit the
 * unified {@link ApiError} envelope.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test void malformedJsonMapsTo400() {
        ResponseEntity<ApiError> r = handler.handleUnreadable(
            new HttpMessageNotReadableException("bad json"));
        assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode());
        assertEquals(400, r.getBody().getStatus());
        assertEquals("BadRequest", r.getBody().getType());
        assertNotNull(r.getBody().getError());
    }

    @Test void typeMismatchMapsTo400() {
        MethodArgumentTypeMismatchException ex =
            new MethodArgumentTypeMismatchException("abc", Integer.class, "page", null, null);
        ResponseEntity<ApiError> r = handler.handleTypeMismatch(ex);
        assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode());
        assertEquals(400, r.getBody().getStatus());
        assertEquals("BadRequest", r.getBody().getType());
    }

    @Test void missingParamMapsTo400() {
        MissingServletRequestParameterException ex =
            new MissingServletRequestParameterException("code", "String");
        ResponseEntity<ApiError> r = handler.handleMissingParam(ex);
        assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode());
        assertEquals(400, r.getBody().getStatus());
        assertEquals("BadRequest", r.getBody().getType());
    }
}
