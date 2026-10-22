package com.recycllux.pelanode.service;

import com.recycllux.pelanode.config.JsonConfig;
import com.recycllux.pelanode.web.ApiException;
import java.time.Instant;

public final class Times {

    private Times() {
    }

    public static Instant parse(String text) {
        try {
            return JsonConfig.parse(text);
        } catch (IllegalArgumentException e) {
            throw ApiException.invalid(e.getMessage());
        }
    }

    public static Instant parseOptional(String text) {
        return text == null || text.isBlank() ? null : parse(text);
    }
}
