# Data storage

Phase 3 adds a receipt repository interface and a DynamoDB implementation using AWS SDK for Java 2.x. The client and table name are injected; the repository does not construct a client or create a table. Domain objects remain independent of DynamoDB serialization.

Every operation requires the authenticated user ID. Keys are `PK = USER#{userId}` and `SK = RECEIPT#{receiptId}`. Save and update reject a snapshot belonging to another user before calling the client. Reads and deletes use the complete user-scoped key; listing uses Query with the user partition and receipt sort-key prefix, following every pagination token. Returned items are checked against their owner and keys.

- Save is create-only and conditionally rejects duplicate keys.
- Find returns an empty Optional for a missing receipt; list returns an empty list.
- Update conditionally replaces an existing snapshot. It preserves receipt identity, createdAt and imageKey; a null category removes the stored category. Missing records or mismatched immutable fields produce ReceiptWriteConflictException. This phase does not provide optimistic version locking: concurrent updates with matching immutable fields are last-write-wins.
- Delete is user-scoped and idempotent, returning whether a record existed.
- Other SDK failures propagate to the caller; they are not treated as missing records.

Totals are stored as DynamoDB numbers and parsed directly as BigDecimal, without floating-point conversion. Dates, timestamps, UUIDs and statuses use their standard string representations. An absent category or explicit DynamoDB NULL maps to a null category.

Unit tests use a mocked DynamoDbClient, including pagination, missing records, conditional failures and user isolation. They do not contact AWS. The original Phase 3 repository milestone created no cloud resources; image storage and API wiring were added in later milestones.

## Phase 10 list filtering

Receipt listing still uses only `Query` with `PK = USER#{authenticatedUserId}` and the `RECEIPT#` sort-key prefix, following every DynamoDB continuation key. The service applies merchant, category and inclusive purchase-date filters to that owner's results, then performs a stable sort. No table scan, new index, DynamoDB filter expression, or API pagination was added. This is suitable for the current personal receipt list; a larger data set or measured query-cost need would require a separately reviewed access pattern. DynamoDB filter expressions do not reduce the read capacity consumed by a Query, so adding one here would not improve that cost.
