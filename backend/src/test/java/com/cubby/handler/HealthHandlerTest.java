package com.cubby.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;

class HealthHandlerTest {

    @Test
    void returnsAnApiGatewayHealthResponse() {
        Map<String, Object> response = new HealthHandler().handleRequest(Map.of(), null);

        assertEquals(200, response.get("statusCode"));
        assertEquals(Map.of("content-type", "application/json"), response.get("headers"));
        assertEquals("{\"status\":\"ok\"}", response.get("body"));
    }
}
