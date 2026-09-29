# Cubby Implementation Roadmap

This document tracks Cubby's application milestones and validation expectations. Follow `AGENTS.md` and the linked GitHub issues.

## Implementation principles

- Complete one milestone at a time and keep changes focused.
- Use mocked clients for unit tests that exercise AWS integrations; tests should not require live cloud resources.
- Validate infrastructure changes with lint, build, and `cdk synth`. Deployment work is separate and must have its own project scope.
- Never commit credentials, real `.env` files, package caches, or generated build output.

## Application roadmap

Phases 1–11 are complete on GitHub `main`. Core infrastructure in `92a4b0e`, private receipt uploads in `406ca0c`, Cognito authentication in `5c74f3c`, receipt CRUD frontend in `3a1474c`, asynchronous OCR in `05b204a`, receipt filtering and sorting in `a0c33b7`, and validation CI/OIDC design in `9eac2c1` passed acceptance review; issues #14–#20 are closed. Phase 12 is implemented and its local backend, frontend and infrastructure checks pass. Deployment and browser/CORS rollout remain separate. Inspect the working tree before editing and preserve existing work.

| Phase | Work | Safe validation |
| --- | --- | --- |
| 1. Repository bootstrap | Java 21 Lambda health handler, React/Vite starter, empty strict TypeScript CDK app, project docs | Backend tests/build; frontend lint/test/build; CDK lint/build/synth. Complete. |
| 2. Domain layer — complete | Immutable `Receipt`, `ReceiptStatus`, DTOs, validation, `ReceiptService` skeleton | JUnit unit tests and backend build; no AWS calls. |
| 3. DynamoDB repository — complete | Repository operations for save, find, user query, update, and delete | Mocked AWS SDK tests; no AWS calls or deployed table. |
| 4. Basic REST API — complete | Health and receipt CRUD handlers; identity abstraction pending Cognito | Handler/service tests using local fixtures; no deployed API. |
| 5. AWS core infrastructure — complete, issue #14 closed | CDK definitions for DynamoDB, Lambda, HTTP API, logging, and scoped IAM | TypeScript lint/build and `cdk synth`; stop before `cdk deploy`. |
| 6. S3 uploads — complete, issue #15 closed | Private bucket and presigned upload flow in code/CDK | Unit tests and synth only; do not create the bucket or upload objects. |
| 7. Cognito — complete, issue #16 closed (`5c74f3c`) | User pool, client, JWT authorizer, and frontend auth code | Backend, frontend and CDK tests plus local synth passed; no identity resources created. |
| 8. Frontend CRUD — complete, issue #17 closed (`3a1474c`) | Dashboard, receipt list/detail, upload, create, edit, and delete flows | Frozen install, frontend lint, 17 mocked tests, and build passed; no AWS calls or deployment. |
| 9. Textract — complete, issue #18 closed (`05b204a`) | Asynchronous OCR handler, status transitions, and metadata updates | Mocked service tests, backend/frontend checks, and local synth passed; no live Textract invocation. |
| 10. Search and filtering — complete, issue #19 closed (`a0c33b7`) | Authenticated-user merchant/category/inclusive date filters and stable sorting | Backend clean test/build (241 tests) and frontend lint/test/build (22 tests) passed; no new index or AWS calls. |
| 11. CI/CD — complete, issue #20 closed (`9eac2c1`) | Read-only GitHub Actions validation and OIDC design for later deployment | Local backend/frontend/infrastructure checks, actionlint, and the hosted main-push validation run passed; no IAM role or deployment. |
| 12. Company expense analytics — complete | Treat each invoice or receipt as one expense; add an authenticated currency-separated Sankey summary and dashboard using reviewed expense fields | Backend clean test/build, frontend lint/test/build, infrastructure lint/build/test/synth passed locally. No deployment or live OCR. |

The project brief remains authoritative for each phase's exact behavior. Keep infrastructure deployment separate from implementation issues.

## Agent review and Git workflow

1. Before editing, read `AGENTS.md`, this file, and the phase-specific source brief. Inspect `git status` and preserve any existing in-progress changes.
2. Work on one bounded phase. After each meaningful subtask, review the diff and run its focused tests; before finishing the phase, run all applicable checks listed in `docs/DEVELOPMENT.md`.
3. Before every commit, run `git diff --check`, inspect `git status`, and review `git diff --cached --name-status`. Scan candidate files for credentials and API keys. Stage only the completed milestone's files.
4. Keep commits small and descriptive. Push each completed, verified milestone to the configured GitHub repository. Never include another agent's unreviewed work, credentials, real `.env` files, virtual environments, package caches, or build output.
5. Report the milestone, files changed, checks and results, commit hash, and push status. Stop before the next phase.
