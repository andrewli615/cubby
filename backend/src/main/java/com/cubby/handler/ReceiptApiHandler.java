package com.cubby.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.cubby.auth.IdentityProvider;
import com.cubby.dto.CreateReceiptRequest;
import com.cubby.dto.UpdateReceiptRequest;
import com.cubby.dto.UploadUrlRequest;
import com.cubby.service.ReceiptConflictException;
import com.cubby.service.ReceiptNotFoundException;
import com.cubby.service.ReceiptService;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * HTTP API payload v2 adapter. Dependencies are injected for local use;
 * the runtime composition root supplies the dependencies and denies access until verified identity is integrated.
 */
public final class ReceiptApiHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {
    private final ReceiptService service;
    private final IdentityProvider identities;
    private final HealthHandler health = new HealthHandler();

    public ReceiptApiHandler(ReceiptService service, IdentityProvider identities) {
        this.service = Objects.requireNonNull(service);
        this.identities = Objects.requireNonNull(identities);
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        try {
            if (event == null || !"2.0".equals(event.get("version"))) {
                throw new IllegalArgumentException("HTTP API payload version 2.0 is required");
            }
            Map<String, Object> requestContext = object(event.get("requestContext"));
            String method = text(object(requestContext.get("http")).get("method"));
            String path = text(event.get("rawPath"));
            if ("/health".equals(path)) {
                return "GET".equals(method) ? health.handleRequest(event, context) : methodNotAllowed("GET");
            }
            boolean upload = "/receipts/upload-url".equals(path);
            boolean collection = "/receipts".equals(path);
            boolean item = path.matches("/receipts/[^/]+");
            if (!collection && !item && !upload) {
                return ApiJson.error(404, "NOT_FOUND", "Route not found");
            }

            var user = identities.authenticatedUserId(requestContext);
            if (user.isEmpty() || user.get().isBlank()) {
                return ApiJson.error(401, "UNAUTHORIZED", "Authentication required");
            }
            String userId = user.get();
            if (upload) {
                return "POST".equals(method)
                        ? ApiJson.response(200, service.createUploadUrl(userId, ApiJson.body(event, UploadUrlRequest.class)))
                        : methodNotAllowed("POST");
            }
            if (collection) {
                return switch (method) {
                    case "POST" -> {
                        var receipt = service.create(userId, ApiJson.body(event, CreateReceiptRequest.class));
                        yield ApiJson.response(201, receipt,
                                Map.of("location", "/receipts/" + receipt.receiptId()));
                    }
                    case "GET" -> ApiJson.response(200, service.list(userId));
                    default -> methodNotAllowed("GET, POST");
                };
            }

            UUID receiptId = receiptId(path.substring("/receipts/".length()));
            return switch (method) {
                case "GET" -> ApiJson.response(200,
                        service.get(userId, receiptId).orElseThrow(ReceiptNotFoundException::new));
                case "PUT" -> ApiJson.response(200,
                        service.update(userId, receiptId, ApiJson.body(event, UpdateReceiptRequest.class)));
                case "DELETE" -> {
                    service.delete(userId, receiptId);
                    yield ApiJson.response(204, null);
                }
                default -> methodNotAllowed("GET, PUT, DELETE");
            };
        } catch (IllegalArgumentException exception) {
            return ApiJson.error(400, "INVALID_REQUEST", "Invalid request");
        } catch (ReceiptNotFoundException exception) {
            return ApiJson.error(404, "NOT_FOUND", "Receipt not found");
        } catch (ReceiptConflictException exception) {
            return ApiJson.error(409, "CONFLICT", "Receipt write conflict");
        } catch (RuntimeException exception) {
            // Never return exception messages, request bodies, credentials or SDK diagnostics.
            return ApiJson.error(500, "INTERNAL_ERROR", "Internal server error");
        }
    }

    private static Map<String, Object> methodNotAllowed(String allow) {
        return ApiJson.response(405,
                Map.of("error", Map.of("code", "METHOD_NOT_ALLOWED", "message", "Method not allowed")),
                Map.of("allow", allow));
    }

    private static UUID receiptId(String value) {
        UUID id = UUID.fromString(value);
        if (!id.toString().equalsIgnoreCase(value)) {
            throw new IllegalArgumentException("A canonical UUID is required");
        }
        return id;
    }

    private static String text(Object value) {
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("Missing request metadata");
        }
        return text;
    }

    private static Map<String, Object> object(Object value) {
        if (!(value instanceof Map<?, ?> source)) {
            throw new IllegalArgumentException("Missing request context");
        }
        Map<String, Object> copy = new HashMap<>();
        for (var entry : source.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new IllegalArgumentException("Invalid request context");
            }
            copy.put(key, entry.getValue());
        }
        return copy;
    }
}
