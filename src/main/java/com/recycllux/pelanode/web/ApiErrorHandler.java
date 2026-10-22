package com.recycllux.pelanode.web;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Maps errors to 404 / 409 / 422 with a small JSON body. */
@RestControllerAdvice
public class ApiErrorHandler {

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Map<String, Object>> api(ApiException e) {
        return body(e.status(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + " " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return body(HttpStatus.UNPROCESSABLE_ENTITY, detail);
    }

    /** Malformed JSON, wrong types, and timestamps without an offset all land here. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, Object>> unreadable(HttpMessageNotReadableException e) {
        Throwable root = e.getMostSpecificCause();
        return body(HttpStatus.UNPROCESSABLE_ENTITY, root.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<Map<String, Object>> badParam(MethodArgumentTypeMismatchException e) {
        return body(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid value for " + e.getName());
    }

    private static ResponseEntity<Map<String, Object>> body(HttpStatus status, String message) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("status", status.value());
        b.put("error", status.getReasonPhrase());
        b.put("message", message);
        b.put("timestamp", Instant.now());
        return ResponseEntity.status(status).body(b);
    }
}
