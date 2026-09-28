# Architecture

## Target architecture

The browser application calls an Amazon API Gateway HTTP API protected by a Cognito JWT authorizer. Java 21 Lambda handlers implement the API and obtain each user identity from the verified JWT subject. DynamoDB stores receipt metadata; private S3 stores receipt images uploaded using short-lived presigned URLs. An S3 event starts asynchronous Textract `AnalyzeExpense` processing, and Lambda updates receipt status and extracted metadata. CloudWatch collects logs. AWS CDK defines infrastructure.

## Phase 1 boundary

The current bootstrap contains only a React landing page, a Java Lambda health handler, and an empty CDK stack. It provisions no cloud resources. Receipt operations, authentication, storage, OCR, and deployment remain future phases.
