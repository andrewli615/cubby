package com.cubby.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import java.util.Map;

/** Minimal API Gateway-compatible Lambda health check for the repository bootstrap. */
public final class HealthHandler
        implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        return Map.of(
                "statusCode", 200,
                "headers", Map.of("content-type", "application/json"),
                "body", "{\"status\":\"ok\"}");
    }
}
