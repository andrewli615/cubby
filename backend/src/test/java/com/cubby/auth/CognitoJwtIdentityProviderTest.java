package com.cubby.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CognitoJwtIdentityProviderTest {
    private static final String SUBJECT = "920a995d-693c-4634-9fcf-985b1ddc0199";
    private final CognitoJwtIdentityProvider identities =
            new CognitoJwtIdentityProvider("us-west-2", "us-west-2_Test", "web-client");

    @Test
    void acceptsAccessTokenSubjectFromAuthorizerClaimsOnly() {
        assertEquals(Optional.of(SUBJECT), identities.authenticatedUserId(context(Map.of(
                "iss", "https://cognito-idp.us-west-2.amazonaws.com/us-west-2_Test",
                "client_id", "web-client", "token_use", "access", "sub", SUBJECT))));
    }

    @Test
    void rejectsMissingAuthorizerAndClientSuppliedClaims() {
        assertTrue(identities.authenticatedUserId(Map.of("sub", SUBJECT,
                "headers", Map.of("authorization", "fake"), "body", Map.of("userId", SUBJECT))).isEmpty());
        assertTrue(identities.authenticatedUserId(Map.of("authorizer", Map.of("iam", Map.of("userId", SUBJECT)))).isEmpty());
    }

    @Test
    void rejectsWrongIssuerAudienceTokenTypeAndMalformedSubject() {
        Map<String, Object> valid = Map.of("iss", "https://cognito-idp.us-west-2.amazonaws.com/us-west-2_Test",
                "client_id", "web-client", "token_use", "access", "sub", SUBJECT);
        for (var bad : java.util.List.of(
                Map.of("iss", "https://evil.invalid", "client_id", "web-client", "token_use", "access", "sub", SUBJECT),
                Map.of("iss", valid.get("iss"), "client_id", "other-client", "token_use", "access", "sub", SUBJECT),
                Map.of("iss", valid.get("iss"), "client_id", "web-client", "token_use", "id", "sub", SUBJECT),
                Map.of("iss", valid.get("iss"), "client_id", "web-client", "token_use", "access", "sub", "attacker"))) {
            assertTrue(identities.authenticatedUserId(context(bad)).isEmpty());
        }
    }

    private static Map<String, Object> context(Map<String, ?> claims) {
        return Map.of("authorizer", Map.of("jwt", Map.of("claims", claims)));
    }
}
