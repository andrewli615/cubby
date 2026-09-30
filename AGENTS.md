# Cubby project instructions

## Current scope

- Follow the current web/serverless plan in `docs/AWS_MILESTONES.md` and the open GitHub issues. Phases 1–12 are complete. Phase 13's local hosting/CORS preparation (`3611c11`) and release configuration/toolchain safeguards (`5613db7`) are implemented. `docs/BUILD_REVIEW.md` lists the remaining verification and the opt-in release-guard limitation. The target account identity and `us-west-2` Region were confirmed by the owner. Do not access AWS, create Amplify resources, bootstrap CDK, deploy, create Cognito users, or upload receipts without a separate explicit deployment instruction. Take one bounded milestone at a time and check the working tree before editing. Issues #1–#11 describe the retired iOS/local Spring/PostgreSQL direction and are closed; do not reopen or implement them.
- The product is a web application. Use React, TypeScript, Vite, Tailwind CSS, and React Router for the frontend; Java 21, Gradle, AWS Lambda, and AWS SDK for Java 2.x for the backend; and AWS CDK with strict TypeScript for infrastructure.
- Do not add Spring Boot, PostgreSQL, or Docker services. Keep infrastructure validation local with `cdk synth`; perform no AWS provisioning as part of Phase 13 preparation.
- Before any first hosted release, work through `docs/BUILD_REVIEW.md` and `docs/DEPLOYMENT_PLAN.md`. Verify CI with the pinned tools and the Amplify monorepo build, use `cubby:release=true` with the confirmed account and actual hosted origin, and review the infrastructure diff. The release guard is opt-in, so a direct CDK deploy command can bypass it; require a reviewed invocation or a guarded deploy command. Keep the initial hosting build that discovers the Amplify URL distinct from the final configured frontend build.
- Treat the current React/TypeScript web application and Java 21 Lambda architecture as authoritative. The retired iOS/Spring/PostgreSQL project has been removed and must not be reintroduced unless the user changes the plan.
- Keep Lambda handlers thin. Add business logic to services and persistence logic to repositories as later phases introduce them.

## Safety and quality

- Never commit credentials or hard-code secrets. Keep S3 private, use least-privilege IAM, validate input, and derive user identity from verified Cognito claims rather than request data.
- Use `BigDecimal` for monetary values and immutable domain objects where practical.
- Prefer focused changes and meaningful tests. Run the checks for every affected component and report any checks that could not run.
- Use the installed AWS skills when a task involves their service area. Keep tests independent of live AWS services unless an issue explicitly requires an integration environment.
- Review changes periodically and at each milestone. Before committing, inspect the diff, run relevant checks, and review the exact staged file list. Never stage credentials, API keys, `.env` files other than `.env.example`, `.venv/`, `venv/`, `node_modules/`, `.pnpm-store/`, `.gradle/`, `build/`, `dist/`, or `cdk.out/`.
- Make small, descriptive commits for each completed and verified milestone, then push those commits to the configured GitHub repository. Stage only files for that milestone; leave other in-progress or unreviewed work untouched.

## AWS implementation guidance

- Place regional resources in the project’s configured Region. Use `us-east-1` only for CloudFront and its required global dependencies.
- Do not use Lambda@Edge or CloudFormation StackSets. Check service and Region support before relying on an AWS service.
- Report access errors with the affected service and operation; do not attempt unrelated account administration.
