# Architecture

## Implemented milestones

Cubby records company expenses from invoices and receipts. Each source document maps to one expense record with merchant, date, total, currency, category and OCR review status. OCR extraction remains separate from the reviewed fields used by reporting. The portfolio MVP represents a fictional company with one Cognito finance user; DynamoDB data stays partitioned by that verified identity. It does not model payment schedules, approvals or invoice line items.

The React frontend, Java 21 Lambda API, Cognito authentication, private S3 originals, asynchronous Textract processing and user-scoped DynamoDB repository form the current architecture. The selected Region is US West (Oregon), `us-west-2`, configured by `cubby:region` in the CDK app. Plain local synthesis leaves the account unresolved; release synthesis requires an explicit `cubby:account` and hosted origin.

## Core CDK resources

- One regional DynamoDB table with string `PK` and `SK`, on-demand capacity, DynamoDB-owned encryption, point-in-time recovery, deletion protection and retention on removal/replacement.
- One Java 21 Lambda using the backend's reproducible ZIP package and `ReceiptLambdaHandler` composition root. Its table and image bucket names are passed through the environment, and the Java SDK uses the Lambda-provided Region.
- One HTTP API with explicit health, receipt CRUD, upload-URL and spending analytics routes using payload format 2.0; no catch-all route or function URL.
- Separate retained CloudWatch log groups for function and API access logs, with 30-day retention. Access logs contain request ID, route key, status, response length and integration latency, excluding bodies, query strings, headers, source IPs and claims.
- One Lambda execution role with only GetItem, PutItem, Query and DeleteItem on the receipt table, plus CreateLogStream and PutLogEvents on its own log group and conditional PutObject on this bucket's `*/originals/*` keys. No Scan, account-wide resource access, managed execution policies, table administration, replication or KMS grants.
- One retained private S3 bucket with SSE-S3 encryption, all public access blocked, ACLs disabled and TLS required. Bucket policies require create-only writes to original keys, deny original deletion and deny upload signatures older than five minutes. The Lambda role has no S3 read, list, delete, ACL, tagging or KMS permissions.
- API invocation permissions bound to the stack's API/account, default stage, HTTP method and path. Receipt IDs use the required path wildcard rather than literal template parameters.

The stack has termination protection. Phase 9 adds a keys-only receipt stream, two Java 21 OCR workers and an SNS completion topic with scoped roles. It defines no networking or cross-Region resources. `cdk synth` writes local templates/assets only. The referenced CDK bootstrap asset bucket is not created by synthesis; deployment and bootstrapping are separate work. See [OCR](OCR.md) for transitions and retries.

## Authorization boundary

Only `GET /health` is public. Receipt and analytics routes require Cognito access tokens at API Gateway. `ReceiptLambdaHandler` derives the owner from the verified JWT subject through its identity provider, never from client-supplied receipt data. The spending summary uses the existing paginated user-partition query and aggregates reviewed expense fields locally. It adds no secondary index; aggregation reads that user's records. Each currency is summarized independently, without conversion.

## Hosting preparation

The stack adds exact-origin HTTP API and private S3 upload CORS when `cubby:webOrigin` is supplied. Plain local synthesis omits browser CORS; release synthesis requires the origin and account under `cubby:release=true`. The repository has no recorded deployment. See the [deployment plan](DEPLOYMENT_PLAN.md) for the remaining hosted checks and [development](DEVELOPMENT.md) for local validation.
