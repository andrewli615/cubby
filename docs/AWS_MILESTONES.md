# Cubby and AWS Explore Milestones

This document is the working roadmap for the coding agent. It combines Cubby's application phases with the AWS Explore activities shown in the user's console screenshot. Follow `AGENTS.md` and stop at the credit-safety gates below.

## User constraints

- The user is on the AWS Free Plan and wants to preserve the remaining credits. Do not deliberately consume Free Tier or promotional credits.
- Do not make billing, budget, payment, spend-limit, or plan changes. In particular, do not configure AWS Budgets. If a task raises billing or payment work, notify the user and leave it to them.
- Do not create or deploy AWS resources, invoke a paid service, or perform an action that may consume credits without explicit user authorization. Local development, tests, and `cdk synth` are allowed; `cdk synth` does not deploy resources.
- If a planned step reaches a credit or billing gate, stop before the action and notify the user. Do not silently substitute another AWS service or mark an Explore activity complete.

AWS documents that the EC2, Bedrock playground, Lambda web app, and RDS/Aurora Explore activities incur service charges deducted from Free Tier credits. The AWS Free Plan ends when credits are exhausted or after six months, whichever comes first. Review current terms and activity eligibility in the AWS Console before any future change to this policy: [Explore activity details](https://docs.aws.amazon.com/en_us/awsaccountbilling/latest/aboutv2/free-tier-plans-activities.html), [Free Plan terms](https://aws.amazon.com/free/terms/), and [supported services for the new AWS experience](https://docs.aws.amazon.com/accounts/latest/reference/supported-services-sign-up-new.html).

## Application roadmap

Complete one phase at a time. Phase 1 is committed to GitHub `main`; Phase 2 is the next application milestone. The working tree may already contain Phase 2 work, so inspect it and continue from the current state instead of recreating or overwriting files.

| Phase | Work | Safe validation |
| --- | --- | --- |
| 1. Repository bootstrap | Java 21 Lambda health handler, React/Vite starter, empty strict TypeScript CDK app, project docs | Backend tests/build; frontend lint/test/build; CDK lint/build/synth. Complete. |
| 2. Domain layer | Immutable `Receipt`, `ReceiptStatus`, DTOs, validation, `ReceiptService` skeleton | JUnit unit tests and backend build; no AWS calls. |
| 3. DynamoDB repository | Repository operations for save, find, user query, update, and delete | Mocked AWS SDK tests; no AWS calls or deployed table. |
| 4. Basic REST API | Health and receipt CRUD handlers; identity abstraction pending Cognito | Handler/service tests using local fixtures; no deployed API. |
| 5. AWS core infrastructure | CDK definitions for DynamoDB, Lambda, HTTP API, logging, and scoped IAM | TypeScript lint/build and `cdk synth`; stop before `cdk deploy`. |
| 6. S3 uploads | Private bucket and presigned upload flow in code/CDK | Unit tests and synth only; do not create the bucket or upload objects. |
| 7. Cognito | User pool, client, JWT authorizer, and frontend auth code | Unit tests and synth only; do not create AWS identity resources. |
| 8. Frontend CRUD | Dashboard, receipt list/detail, upload, edit, and delete flows | Frontend lint/test/build against local mocks. |
| 9. Textract | Asynchronous OCR handler, status transitions, and metadata updates | Mocked service tests and synth only; do not invoke Textract. |
| 10. Search and filtering | Merchant/category/date filters and sorting | Frontend and backend tests; defer indexes until justified. |
| 11. CI/CD | GitHub Actions validation; OIDC design for later deployment | Workflow validation only; do not create IAM roles or enable deployment. |

The project brief remains authoritative for each phase's exact behavior. Do not advance to a later phase until the user requests it.

## AWS Explore activity tracker

The screenshot showed five activities at `$20` each and `0 of 5` completed. Treat those values as the screenshot's snapshot, not a guarantee of current eligibility or reward. AWS Console activity status is authoritative.

| Activity in screenshot | Current decision | Agent boundary |
| --- | --- | --- |
| Launch an instance using EC2 | Deferred to preserve credits | Do not launch an instance or create supporting resources. |
| Use a foundation model in the Bedrock playground | Deferred to preserve credits | Do not invoke a model or create Bedrock resources. |
| Set up a cost budget using AWS Budgets | Not performed by the agent | This is a billing-related activity; notify the user if it becomes relevant. |
| Create a web app using AWS Lambda | Build Cubby locally; deployment deferred | Implement and synthesize code only. Do not deploy a function or function URL. |
| Create an Aurora or RDS database | Deferred to preserve credits and keep v1 serverless | Do not create a database or supporting resources. |

These activities are not part of Cubby v1's implementation unless the user later changes the product scope. Completing the source code for a milestone does not mean its AWS Explore activity is complete.

## Agent review and Git workflow

1. Before editing, read `AGENTS.md`, this file, and the phase-specific source brief. Inspect `git status` and preserve any existing in-progress changes.
2. Work on one bounded phase. After each meaningful subtask, review the diff and run its focused tests; before finishing the phase, run all applicable checks listed in `docs/DEVELOPMENT.md`.
3. Before every commit, run `git diff --check`, inspect `git status`, and review `git diff --cached --name-status`. Scan candidate files for credentials and API keys. Stage only the completed milestone's files.
4. Keep commits small and descriptive. Push each completed, verified milestone to the configured GitHub repository. Never include another agent's unreviewed work, credentials, real `.env` files, virtual environments, package caches, or build output.
5. Report the milestone, files changed, checks and results, commit hash, push status, and any AWS action deferred by the credit or billing rules. Stop before the next phase.
