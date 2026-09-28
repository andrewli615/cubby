package com.cubby.auth;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Reads only claims that API Gateway's configured JWT authorizer has verified. */
public final class CognitoJwtIdentityProvider implements IdentityProvider {
    private final String issuer;
    private final String clientId;

    public CognitoJwtIdentityProvider(String region, String userPoolId, String clientId) {
        if (region == null || region.isBlank() || userPoolId == null || userPoolId.isBlank()
                || clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("Cognito configuration is required");
        }
        this.issuer = "https://cognito-idp." + region + ".amazonaws.com/" + userPoolId;
        this.clientId = clientId;
    }

    @Override
    public Optional<String> authenticatedUserId(Map<String, Object> requestContext) {
        Map<?, ?> authorizer = map(requestContext == null ? null : requestContext.get("authorizer"));
        Map<?, ?> jwt = map(authorizer.get("jwt"));
        Map<?, ?> claims = map(jwt.get("claims"));
        if (!issuer.equals(claims.get("iss")) || !clientId.equals(claims.get("client_id"))
                || !"access".equals(claims.get("token_use"))) {
            return Optional.empty();
        }
        Object subject = claims.get("sub");
        if (!(subject instanceof String value)) {
            return Optional.empty();
        }
        try {
            UUID parsed = UUID.fromString(value);
            return parsed.toString().equalsIgnoreCase(value) ? Optional.of(parsed.toString()) : Optional.empty();
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private static Map<?, ?> map(Object value) {
        return value instanceof Map<?, ?> result ? result : Map.of();
    }
}
