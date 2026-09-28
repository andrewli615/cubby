package com.cubby.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cubby.auth.CognitoJwtIdentityProvider;
import com.cubby.service.ReceiptService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ReceiptLambdaHandlerTest {
    private final ReceiptService service = mock(ReceiptService.class);
    private final ReceiptLambdaHandler handler = new ReceiptLambdaHandler(service,
            new CognitoJwtIdentityProvider("us-west-2", "us-west-2_Test", "web-client"));

    @Test
    void healthRemainsPublicWithoutCallingPersistence() {
        assertEquals(200, handler.handleRequest(
                ReceiptApiHandlerTest.event("GET", "/health", null), null).get("statusCode"));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET /receipts", "POST /receipts", "POST /receipts/upload-url",
            "GET /receipts/920a995d-693c-4634-9fcf-985b1ddc0199",
            "PUT /receipts/920a995d-693c-4634-9fcf-985b1ddc0199",
            "DELETE /receipts/920a995d-693c-4634-9fcf-985b1ddc0199"})
    void deniesReceiptAccessEvenWhenCallerSuppliesIdentityMetadata(String route) {
        String[] parts = route.split(" ");
        var event = ReceiptApiHandlerTest.event(parts[0], parts[1], "{\"userId\":\"alice\"}");
        event.put("headers", Map.of("x-user-id", "alice"));
        event.put("requestContext", Map.of("http", Map.of("method", parts[0]),
                "authorizer", Map.of("jwt", Map.of("claims", Map.of("sub", "alice")),
                        "iam", Map.of("userId", "alice"))));
        assertEquals(401, handler.handleRequest(event, null).get("statusCode"));
        verifyNoInteractions(service);
    }

    @Test
    void delegatesUsingOnlyVerifiedJwtSubjectDespiteSpoofedRequestIdentity() {
        String subject = "920a995d-693c-4634-9fcf-985b1ddc0199";
        var event = ReceiptApiHandlerTest.event("GET", "/receipts", null);
        event.put("userId", "attacker");
        event.put("headers", Map.of("x-user-id", "attacker"));
        event.put("requestContext", Map.of("http", Map.of("method", "GET"),
                "authorizer", Map.of("jwt", Map.of("claims", Map.of(
                        "iss", "https://cognito-idp.us-west-2.amazonaws.com/us-west-2_Test",
                        "client_id", "web-client", "token_use", "access", "sub", subject)))));
        when(service.list(subject)).thenReturn(java.util.List.of());
        assertEquals(200, handler.handleRequest(event, null).get("statusCode"));
        verify(service).list(subject);
    }
}
