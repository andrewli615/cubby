# Data storage

Phase 3 adds a receipt repository interface and a DynamoDB implementation using AWS SDK for Java 2.x. The client and table name are injected; the repository does not construct a client or create a table. Domain objects remain independent of DynamoDB serialization.

Every operation requires the authenticated user ID. Keys are `PK = USER#{userId}` and `SK = RECEIPT#{receiptId}`. Save and update reject a snapshot belonging to another user before calling the client. Reads and deletes use the complete user-scoped key; listing uses Query with the user partition and receipt sort-key prefix, following every pagination token. Returned items are checked against their owner and keys.

- Save is create-only and conditionally rejects duplicate keys.
- Find returns an empty Optional for a missing receipt; list returns an empty list.
- Update conditionally replaces an existing snapshot. It preserves receipt identity, createdAt and imageKey; a null category removes the stored category. Missing records or mismatched immutable fields produce ReceiptWriteConflictException. This phase does not provide optimistic version locking: concurrent updates with matching immutable fields are last-write-wins.
- Delete is user-scoped and idempotent, returning whether a record existed.
- Other SDK failures propagate to the caller; they are not treated as missing records.

Totals are stored as DynamoDB numbers and parsed directly as BigDecimal, without floating-point conversion. Dates, timestamps, UUIDs and statuses use their standard string representations. An absent category or explicit DynamoDB NULL maps to a null category.

Unit tests use a mocked DynamoDbClient, including pagination, missing records, conditional failures and user isolation. They do not contact AWS. This phase creates no table, bucket or other cloud resource. Receipt image storage and API wiring remain later phases.
