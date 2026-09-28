# Cubby Implementation Roadmap

This document tracks Cubby's application milestones and validation expectations. Follow `AGENTS.md` and the linked GitHub issues.

## Implementation principles

- Complete one milestone at a time and keep changes focused.
- Use mocked clients for unit tests that exercise AWS integrations; tests should not require live cloud resources.
- Validate infrastructure changes with lint, build, and `cdk synth`. Deployment work is separate and must have its own project scope.
- Never commit credentials, real `.env` files, package caches, or generated build output.

## Application roadmap

Phases 1–3 are complete on GitHub `main`. Phase 4 implementation is committed as `bf9a2de`; issue #13 remains open for review and acceptance. Issue #14 is next after issue #13 is complete. Inspect the working tree before editing and preserve existing work.

| Phase | Work | Safe validation |
| --- | --- | --- |
| 1. Repository bootstrap | Java 21 Lambda health handler, React/Vite starter, empty strict TypeScript CDK app, project docs | Backend tests/build; frontend lint/test/build; CDK lint/build/synth. Complete. |
| 2. Domain layer — complete | Immutable `Receipt`, `ReceiptStatus`, DTOs, validation, `ReceiptService` skeleton | JUnit unit tests and backend build; no AWS calls. |
| 3. DynamoDB repository — complete | Repository operations for save, find, user query, update, and delete | Mocked AWS SDK tests; no AWS calls or deployed table. |
| 4. Basic REST API — implemented, issue #13 open | Health and receipt CRUD handlers; identity abstraction pending Cognito | Handler/service tests using local fixtures; no deployed API. |
| 5. AWS core infrastructure | CDK definitions for DynamoDB, Lambda, HTTP API, logging, and scoped IAM | TypeScript lint/build and `cdk synth`; stop before `cdk deploy`. |
| 6. S3 uploads | Private bucket and presigned upload flow in code/CDK | Unit tests and synth only; do not create the bucket or upload objects. |
| 7. Cognito | User pool, client, JWT authorizer, and frontend auth code | Unit tests and synth only; do not create AWS identity resources. |
| 8. Frontend CRUD | Dashboard, receipt list/detail, upload, edit, and delete flows | Frontend lint/test/build against local mocks. |
| 9. Textract | Asynchronous OCR handler, status transitions, and metadata updates | Mocked service tests and synth only; do not invoke Textract. |
| 10. Search and filtering | Merchant/category/date filters and sorting | Frontend and backend tests; defer indexes until justified. |
| 11. CI/CD | GitHub Actions validation; OIDC design for later deployment | Workflow validation only; do not create IAM roles or enable deployment. |

The project brief remains authoritative for each phase's exact behavior. Keep infrastructure deployment separate from implementation issues.

## Agent review and Git workflow

1. Before editing, read `AGENTS.md`, this file, and the phase-specific source brief. Inspect `git status` and preserve any existing in-progress changes.
2. Work on one bounded phase. After each meaningful subtask, review the diff and run its focused tests; before finishing the phase, run all applicable checks listed in `docs/DEVELOPMENT.md`.
3. Before every commit, run `git diff --check`, inspect `git status`, and review `git diff --cached --name-status`. Scan candidate files for credentials and API keys. Stage only the completed milestone's files.
4. Keep commits small and descriptive. Push each completed, verified milestone to the configured GitHub repository. Never include another agent's unreviewed work, credentials, real `.env` files, virtual environments, package caches, or build output.
5. Report the milestone, files changed, checks and results, commit hash, and push status. Stop before the next phase.
