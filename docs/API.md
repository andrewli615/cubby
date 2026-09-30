# API

Phase 4 implements a local, dependency-injected HTTP API payload v2 adapter in `ReceiptApiHandler`. It delegates receipt operations to `ReceiptService`; `DefaultReceiptService` applies business rules through `ReceiptRepository`. The API adapter constructs no AWS clients; the Lambda composition root wires the repository and upload presigner. The existing standalone `HealthHandler` remains available and is also used by the router.

## Routes

| Method | Path | Success | Behavior |
| --- | --- | --- | --- |
| GET | /health | 200 | Public JSON health response |
| POST | /receipts | 201 | Create; returns a receipt and Location header |
| POST | /receipts/upload-url | 200 | Returns an owner-scoped, create-only presigned PUT authorization |
| GET | /receipts | 200 | Returns an array of the caller's receipts, including an empty array |
| GET | /receipts/{receiptId} | 200 | Returns the caller's receipt |
| PUT | /receipts/{receiptId} | 200 | Replaces editable metadata |
| DELETE | /receipts/{receiptId} | 204 | Deletes the caller's receipt; empty response body |
| GET | /analytics/spending | 200 | Returns currency-separated Sankey links for the caller's expenses |

Receipt IDs must be canonical UUIDs. Unknown paths return 404; unsupported methods on known routes return 405 with an Allow header. PATCH and OCR routes are not implemented; OCR runs asynchronously through stream and SNS events.

## Receipt list filters (Phase 10)

`GET /receipts` accepts optional query parameters `merchant`, `category`, `dateFrom`, `dateTo`, and `sort`. Merchant is a case-insensitive substring match; category is a case-insensitive exact match. Leading and trailing spaces in these text filters are ignored, and blank values mean no filter. Dates are ISO `YYYY-MM-DD` and both boundaries are inclusive; either boundary can be used alone. An end date before the start date is invalid.

The default sort is `date_desc` (newest purchase date first). Other values are `date_asc`, `merchant_asc`, `merchant_desc`, `total_asc`, and `total_desc`. Equal sort values retain their original user-partition query order. Unknown or repeated parameters, invalid dates, unsupported sort values, and text filters over 200 characters return `400 INVALID_REQUEST`. A valid filter with no matches returns `200 []`. The authenticated subject always selects the user partition; a `userId` query parameter is rejected.

Example: `GET /receipts?merchant=Corner+Shop&category=Office&dateFrom=2026-09-01&dateTo=2026-09-30&sort=total_desc`.

## Spending analytics (Phase 12)

`GET /analytics/spending` accepts optional `dateFrom` and `dateTo` ISO dates. Both boundaries are inclusive, either can be used alone, and a reversed range or unknown/repeated parameter returns `400 INVALID_REQUEST`. The endpoint uses the authenticated Cognito subject and the existing paginated user query; clients cannot select a user or company. It aggregates the stored reviewed expense fields, not the separate OCR extraction. A missing category is grouped as `Uncategorized`.

The response contains one Sankey graph per currency. Link IDs are stable within the response and separate category and merchant nodes:

```json
{"currencies":[{"currency":"CAD","total":42.5,"nodes":[{"id":"spending","label":"Company spending"},{"id":"category:Office","label":"Office"},{"id":"merchant:Office:Paper Co","label":"Paper Co"}],"links":[{"source":"spending","target":"category:Office","value":42.5},{"source":"category:Office","target":"merchant:Office:Paper Co","value":42.5}]}]}
```

Category links and merchant links each conserve the currency total. Currency totals are never combined or converted. Aggregation reads every paginated record for the authenticated user and applies the date range in the service; this suits the portfolio-sized dataset and adds no index. A future larger dataset may need a different aggregation strategy.

## Requests and identity

The adapter reads `version: "2.0"`, `rawPath`, and `requestContext.http.method`. The body is a JSON string, optionally base64 encoded when `isBase64Encoded` is true. JSON parsing rejects duplicate fields, unknown fields, trailing content, invalid values and scalar type coercion.

Receipt routes require an injected `IdentityProvider`. Production uses the verified Cognito JWT `sub` claim from API Gateway request context; body, query, path parameters and client identity headers cannot select the owner. Body identity fields are rejected. Anonymous requests return 401 without calling the service. The API adapter supports explicit constructor injection for local tests. No API has been deployed.

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

The image key must belong to the authenticated subject followed by `/`; empty, escaped and traversal path segments are rejected. Create receipt metadata after the upload succeeds, using the returned `imageKey`. Metadata creation validates the namespace but does not check object existence or inspect file bytes.

PUT uses the same metadata fields except `imageKey`. It requires merchant, purchaseDate, total and currency; category is optional, and null or omission clears it. The original image, status, receipt ID, owner and creation time are preserved. Negative totals are rejected; monetary values use BigDecimal. The service generates UUIDs and timestamps, starts receipts as UPLOADED, and keeps updatedAt from moving backwards.

Responses serialize the receipt's fields directly: UUID and ISO date/time strings, an enum status string, a JSON number for total, and a nullable category. Phase 9 adds `REVIEW_NEEDED`, `OCR_FAILED` transitions and an optional `ocr` object containing separate extracted merchant, purchase date, total, currency and `reviewRequired`. The internal Textract job ID is not returned. Receipt responses use `Cache-Control: no-store`.

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

## Private upload authorization

Send `POST /receipts/upload-url` through the same verified identity boundary:

```json
{"fileName":"receipt.png","contentType":"image/png","contentLength":12345}
```

The file name must be plain (no path separators, control characters or surrounding whitespace) and at most 255 characters. Allowed declared types are exactly `image/jpeg`, `image/png` and `application/pdf`. The byte length is a required integer from 1 through 10,485,760 (a product upload limit). Metadata validation does not prove that the bytes are an image/PDF; content inspection belongs to later processing.

The service assigns `{verifiedSubject}/originals/{randomUuid}`; file names never become storage keys or stored S3 metadata. Owner subjects must be 1–128 ASCII letters, digits, underscores or hyphens. The caller cannot supply the owner, key or expiry.

The response contains `uploadUrl`, `imageKey`, `expiresAt`, `method: "PUT"` and `headers` (a map of header names to arrays of values). Expiry comes from the SDK presigner with a five-minute signature duration; temporary credentials can expire earlier. Send the raw file bytes with every returned signed header, including `content-type`, the exact `content-length` and `if-none-match: *`. HTTP clients may set Host and Content-Length automatically. Never send multipart form data to this URL.

URLs are bearer authorizations: do not log or persist them. API responses use `Cache-Control: no-store` and the response object's diagnostic string redacts authorization details. Signing neither uploads bytes nor writes receipt metadata.

The [S3 conditional-write contract](https://docs.aws.amazon.com/AmazonS3/latest/userguide/conditional-writes-enforce.html) prevents replacement at an existing key. The bucket also denies deletion of originals, and its policy rejects signatures older than five minutes. Receipt deletion removes metadata only and retains the original object; retention cleanup would require a separate design. Concurrent/repeated uploads may return S3 409/412; request a new authorization/key when needed.

The Lambda entry point requires verified Cognito identity on this route. CDK configures browser CORS for one exact HTTPS origin when `cubby:webOrigin` is supplied; plain local synthesis omits CORS. The hosted preflight and upload smoke tests remain pending, as does deployment.
