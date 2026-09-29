# Cubby project instructions

## Current scope

- Follow the current web/serverless plan in `docs/AWS_MILESTONES.md` and the open GitHub issues. Phases 1–12 are complete. Phase 13 is authorized for local hosting/deployment preparation: finish the Amplify build configuration, exact-origin CORS support, documentation, and local validation described in `docs/DEPLOYMENT_PLAN.md`. The target account identity and `us-west-2` Region were confirmed by the owner. Do not access AWS, create Amplify resources, bootstrap CDK, deploy, create Cognito users, or upload receipts without a separate explicit deployment instruction. Take one bounded milestone at a time and check the working tree before editing. Issues #1–#11 describe the retired iOS/local Spring/PostgreSQL direction and are closed; do not reopen or implement them.
- The product is a web application. Use React, TypeScript, Vite, Tailwind CSS, and React Router for the frontend; Java 21, Gradle, AWS Lambda, and AWS SDK for Java 2.x for the backend; and AWS CDK with strict TypeScript for infrastructure.
- Do not add Spring Boot, PostgreSQL, or Docker services. Keep infrastructure validation local with `cdk synth`; perform no AWS provisioning as part of Phase 13 preparation.
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
