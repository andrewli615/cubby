# Architecture

## Implemented milestones

Phases 1–4 provide the React bootstrap, immutable receipt domain, user-scoped DynamoDB repository, and local REST handler/service layers. Phase 5 defines the core regional infrastructure in strict TypeScript CDK. Phase 6 adds private, create-only S3 upload definitions and local signing. The selected Region is US West (Oregon), `us-west-2`, configured by `cubby:region` in the CDK app. The account remains a CloudFormation token; synthesis requires no account lookup.

## Core CDK resources

- One regional DynamoDB table with string `PK` and `SK`, on-demand capacity, DynamoDB-owned encryption, point-in-time recovery, deletion protection and retention on removal/replacement.
- One Java 21 Lambda using the backend's reproducible ZIP package and `ReceiptLambdaHandler` composition root. Its table and image bucket names are passed through the environment, and the Java SDK uses the Lambda-provided Region.
- One HTTP API with explicit health, receipt CRUD and upload-URL routes using payload format 2.0; no catch-all route or function URL.
- Separate retained CloudWatch log groups for function and API access logs, with 30-day retention. Access logs contain request ID, route key, status, response length and integration latency, excluding bodies, query strings, headers, source IPs and claims.
- One Lambda execution role with only GetItem, PutItem, Query and DeleteItem on the receipt table, plus CreateLogStream and PutLogEvents on its own log group and conditional PutObject on this bucket's `*/originals/*` keys. No Scan, account-wide resource access, managed execution policies, table administration, replication or KMS grants.
- One retained private S3 bucket with SSE-S3 encryption, all public access blocked, ACLs disabled and TLS required. Bucket policies require create-only writes to original keys, deny original deletion and deny upload signatures older than five minutes. The Lambda role has no S3 read, list, delete, ACL, tagging or KMS permissions.
- API invocation permissions bound to the stack's API/account, default stage, HTTP method and path. Receipt IDs use the required path wildcard rather than literal template parameters.

The stack has termination protection. It defines no networking, OCR resources, identity resources or cross-Region resources. `cdk synth` writes local templates/assets only. The referenced CDK bootstrap asset bucket is not created by synthesis; deployment and bootstrapping are separate work.

## Authorization boundary

Only `GET /health` is public. All receipt routes require IAM authorization at API Gateway as an interim infrastructure guard. `ReceiptLambdaHandler` additionally uses an identity provider that always rejects receipt access, including requests containing claims or IAM metadata. It does not convert IAM callers or client-provided data into receipt owners.

Receipt operations remain available through explicit dependency injection in local tests. A verified Cognito identity provider and corresponding JWT authorizer are deferred to the authentication milestone. IAM authorization alone is not presented as completed end-user authentication.

## Later milestones

Cognito, frontend CRUD and asynchronous Textract processing remain separate issues. Browser CORS origins are intentionally unconfigured until the frontend integration defines its trusted origins. No deployment has been performed. See [API](API.md) for the local contract and [development](DEVELOPMENT.md) for validation commands.
