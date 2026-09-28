# API

Phase 4 implements a local, dependency-injected HTTP API payload v2 adapter in `ReceiptApiHandler`. It delegates receipt operations to `ReceiptService`; `DefaultReceiptService` applies business rules through `ReceiptRepository`. No handler constructs an AWS client. The existing standalone `HealthHandler` remains available and is also used by the router.

## Routes

| Method | Path | Success | Behavior |
| --- | --- | --- | --- |
| GET | /health | 200 | Public JSON health response |
| POST | /receipts | 201 | Create; returns a receipt and Location header |
| GET | /receipts | 200 | Returns an array of the caller's receipts, including an empty array |
| GET | /receipts/{receiptId} | 200 | Returns the caller's receipt |
| PUT | /receipts/{receiptId} | 200 | Replaces editable metadata |
| DELETE | /receipts/{receiptId} | 204 | Deletes the caller's receipt; empty response body |

Receipt IDs must be canonical UUIDs. Unknown paths return 404; unsupported methods on known routes return 405 with an Allow header. PATCH, uploads and OCR routes are not implemented.

## Requests and identity

The adapter reads `version: "2.0"`, `rawPath`, and `requestContext.http.method`. The body is a JSON string, optionally base64 encoded when `isBase64Encoded` is true. JSON parsing rejects duplicate fields, unknown fields, trailing content, invalid values and scalar type coercion.

Receipt routes require an injected `IdentityProvider`. It receives only request-context metadata and must return a verified authenticated subject or an empty Optional. No production identity provider or default test user is installed. Body, query, path parameters and client identity headers cannot select the owner; body identity fields are rejected. Anonymous requests return 401 without calling the service. Cognito verification remains a later phase. The API adapter requires explicit constructor injection for local tests. Phase 5 adds `ReceiptLambdaHandler` as the packaged composition root: it uses an identity provider that rejects all receipt requests until verified authentication is integrated. The CDK receipt routes also require IAM authorization; this is not end-user identity integration. No API has been deployed.

Create example:

```json
{
  "merchant": "Office Store",
  "purchaseDate": "2026-09-28",
  "total": 19.99,
  "currency": "CAD",
  "category": "Office",
  "imageKey": "authenticated-subject/receipt-image"
}
```

The image key must begin with the authenticated subject followed by `/` and a nonblank suffix. This phase checks the namespace only; it does not upload, sign URLs or check object existence. Later upload code must issue keys in that namespace.

PUT uses the same metadata fields except `imageKey`. It requires merchant, purchaseDate, total and currency; category is optional, and null or omission clears it. The original image, status, receipt ID, owner and creation time are preserved. Negative totals are rejected; monetary values use BigDecimal. The service generates UUIDs and timestamps, starts receipts as UPLOADED, and keeps updatedAt from moving backwards.

Responses serialize the receipt's fields directly: UUID and ISO date/time strings, an enum status string, a JSON number for total, and a nullable category. Receipt responses use `Cache-Control: no-store`.

## Errors and local validation

Errors have the form:

```json
{"error":{"code":"NOT_FOUND","message":"Receipt not found"}}
```

| Status | Code | Meaning |
| --- | --- | --- |
| 400 | INVALID_REQUEST | Invalid envelope, body, UUID or domain values |
| 401 | UNAUTHORIZED | Identity provider did not authenticate the caller |
| 404 | NOT_FOUND | Unknown route or receipt missing from the caller's partition |
| 405 | METHOD_NOT_ALLOWED | Unsupported method |
| 409 | CONFLICT | Repository conditional-write conflict, including a deletion racing with an update |
| 500 | INTERNAL_ERROR | Unexpected failure; internal exception text is not returned |

Deleting a missing receipt returns 404. Concurrent metadata writes retain the repository's documented last-write-wins behavior; this phase adds no version-locking contract.

Handler tests use mocked services and identity providers. Service tests use a mocked repository and fixed clock/UUIDs. Local integration tests exercise the real handler and service with an in-memory mocked repository. Run `cd backend; .\gradlew.bat clean test build`. Tests do not require live AWS services or a deployed API.
