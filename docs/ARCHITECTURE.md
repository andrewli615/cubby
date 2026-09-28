# Architecture

## Target architecture

The browser application calls an Amazon API Gateway HTTP API protected by a Cognito JWT authorizer. Java 21 Lambda handlers implement the API and obtain each user identity from the verified JWT subject. DynamoDB stores receipt metadata; private S3 stores receipt images uploaded using short-lived presigned URLs. An S3 event starts asynchronous Textract `AnalyzeExpense` processing, and Lambda updates receipt status and extracted metadata. CloudWatch collects logs. AWS CDK defines infrastructure.

## Current implementation

The backend includes the health and receipt CRUD handler layer, receipt domain validation, a service layer, and a DynamoDB repository implementation. Handlers receive identity through an injected provider; production Cognito verification and Lambda runtime composition are not yet connected. The frontend remains a starter application. S3 uploads, Cognito integration, OCR, and the corresponding CDK resources are future milestones. See `docs/AWS_MILESTONES.md` for current status.
