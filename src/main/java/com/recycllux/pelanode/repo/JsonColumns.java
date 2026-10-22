package com.recycllux.pelanode.repo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JsonColumns {

    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {};
    private static final TypeReference<List<Double>> NUMBERS = new TypeReference<>() {};
    private static final TypeReference<Map<String, Object>> OBJECT = new TypeReference<>() {};

    private final ObjectMapper mapper;

    public String write(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot serialise column value", e);
        }
    }

    public List<String> strings(String json) {
        return read(json, STRINGS);
    }

    public List<Double> numbers(String json) {
        return read(json, NUMBERS);
    }

    public Map<String, Object> object(String json) {
        return read(json, OBJECT);
    }

    private <T> T read(String json, TypeReference<T> type) {
        if (json == null) {
            return null;
        }
        try {
            return mapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Corrupt JSON column: " + json, e);
        }
    }

    public static Timestamp ts(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    public static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp t = rs.getTimestamp(column);
        return t == null ? null : t.toInstant();
    }

    public static Double dbl(ResultSet rs, String column) throws SQLException {
        double v = rs.getDouble(column);
        return rs.wasNull() ? null : v;
    }

    public static Integer integer(ResultSet rs, String column) throws SQLException {
        int v = rs.getInt(column);
        return rs.wasNull() ? null : v;
    }
}
