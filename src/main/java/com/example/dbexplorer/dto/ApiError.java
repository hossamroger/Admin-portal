package com.example.dbexplorer.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Unified error envelope shared by {@code GlobalExceptionHandler} and the
 * servlet-level {@code AuthFilter}. Serialises to
 * {@code {"error": ..., "type": ..., "status": ..., "requestId": ...}} with
 * null fields omitted.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiError {

    private final String error;
    private final String type;
    private final Integer status;
    private final String requestId;

    public ApiError(String error, String type, Integer status, String requestId) {
        this.error = error;
        this.type = type;
        this.status = status;
        this.requestId = requestId;
    }

    public static ApiError of(String error, String type, int status, String requestId) {
        return new ApiError(error, type, status, requestId);
    }

    public String getError() {
        return error;
    }

    public String getType() {
        return type;
    }

    public Integer getStatus() {
        return status;
    }

    public String getRequestId() {
        return requestId;
    }
}
