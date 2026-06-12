package com.example.dbexplorer.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Unified success envelope for write/action endpoints.
 *
 * <p>Serialises to {@code {"message": "..."}} and, when supplied, merges any
 * extra payload fields at the top level (e.g. {@code id}, {@code affected}) so
 * the JSON shape stays backward-compatible with the existing SPA client, which
 * reads fields such as {@code res.id} directly.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse {

    private final String message;
    private final Map<String, Object> data;

    private ApiResponse(String message, Map<String, Object> data) {
        this.message = message;
        this.data = (data == null || data.isEmpty()) ? null : data;
    }

    /** Success body carrying only a human-readable message: {@code {"message": ...}}. */
    public static ApiResponse ok(String message) {
        return new ApiResponse(message, null);
    }

    /** Success body with a message plus one extra top-level field. */
    public static ApiResponse ok(String message, String key, Object value) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(key, value);
        return new ApiResponse(message, m);
    }

    /** Success body with a message plus arbitrary extra top-level fields. */
    public static ApiResponse ok(String message, Map<String, Object> data) {
        return new ApiResponse(message, data);
    }

    public String getMessage() {
        return message;
    }

    /** Extra payload fields are flattened to the top level of the JSON object. */
    @com.fasterxml.jackson.annotation.JsonAnyGetter
    public Map<String, Object> getData() {
        return data;
    }
}
