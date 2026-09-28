package com.cubby.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/** JSON and API Gateway proxy-envelope handling only. */
final class ApiJson {
    private static final JsonMapper JSON = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .withCoercionConfig(LogicalType.Textual, config -> config
                    .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                    .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                    .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail))
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    private ApiJson() {}

    static <T> T body(Map<String, Object> event, Class<T> type) {
        if (!(event.get("body") instanceof String body) || body.isBlank()) {
            throw new IllegalArgumentException("A JSON request body is required");
        }
        Object encoded = event.get("isBase64Encoded");
        if (encoded != null && !(encoded instanceof Boolean)) {
            throw new IllegalArgumentException("Invalid base64 flag");
        }
        byte[] bytes = Boolean.TRUE.equals(encoded)
                ? Base64.getDecoder().decode(body) : body.getBytes(StandardCharsets.UTF_8);
        try {
            T request = JSON.readValue(bytes, type);
            if (request == null) {
                throw new IllegalArgumentException("A JSON object is required");
            }
            return request;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Invalid JSON request", exception);
        }
    }

    static Map<String, Object> response(int status, Object body) {
        return response(status, body, Map.of());
    }

    static Map<String, Object> response(int status, Object body, Map<String, String> extraHeaders) {
        Map<String, String> headers = new HashMap<>(extraHeaders);
        headers.put("content-type", "application/json");
        headers.put("cache-control", "no-store");
        try {
            return Map.of("statusCode", status, "headers", Map.copyOf(headers),
                    "body", status == 204 ? "" : JSON.writeValueAsString(body), "isBase64Encoded", false);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize response", exception);
        }
    }

    static Map<String, Object> error(int status, String code, String message) {
        return response(status, Map.of("error", Map.of("code", code, "message", message)));
    }
}
