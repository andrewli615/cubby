# Data storage

The planned receipt metadata store is Amazon DynamoDB. The proposed table uses `PK = USER#{userId}` and `SK = RECEIPT#{receiptId}`, so each user query is scoped to the authenticated Cognito subject. Receipt images belong in a private S3 bucket. No table or bucket is created in Phase 1.
