package com.cubby.auth;

import java.util.Map;
import java.util.Optional;

/**
 * Resolves an authenticated subject from trusted request-context metadata.
 * Implementations must not use body, query, path or client-supplied identity headers.
 * Production reads only API Gateway verified Cognito JWT claims.
 */
@FunctionalInterface
public interface IdentityProvider {
    Optional<String> authenticatedUserId(Map<String, Object> requestContext);
}
