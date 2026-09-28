package com.cubby.auth;

import java.util.Map;
import java.util.Optional;

/**
 * Resolves an authenticated subject from trusted request-context metadata.
 * Implementations must not use body, query, path or client-supplied identity headers.
 * Phase 4 injects local test providers; verified Cognito claims are a later integration.
 */
@FunctionalInterface
public interface IdentityProvider {
    Optional<String> authenticatedUserId(Map<String, Object> requestContext);
}
