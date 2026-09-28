# Cubby project instructions

## Current scope

- Follow the current web/serverless plan in `docs/AWS_MILESTONES.md` and the open GitHub issues. Phase 1 (repository bootstrap) and Phase 2 (receipt domain layer) are complete. Start with GitHub issue #12 (DynamoDB receipt repository), then take one issue at a time in order through #20. Check the current working tree before editing because a phase may already be in progress. The old GitHub issues #1-#11 describe the retired iOS/local Spring/PostgreSQL direction and are closed; do not reopen or implement them.
- The product is a web application. Use React, TypeScript, Vite, Tailwind CSS, and React Router for the frontend; Java 21, Gradle, AWS Lambda, and AWS SDK for Java 2.x for the backend; and AWS CDK with strict TypeScript for infrastructure.
- Do not add Spring Boot, PostgreSQL, Docker services, or production AWS resources. Do not deploy unless the user explicitly asks.
- Treat the current React/TypeScript web application and Java 21 Lambda architecture as authoritative. Do not restore the earlier iOS-first architecture; the retained `ios/` prototype is historical and out of the current build scope unless the user changes the plan.
- Keep Lambda handlers thin. Add business logic to services and persistence logic to repositories as later phases introduce them.

## Safety and quality

- Never commit credentials or hard-code secrets. Keep S3 private, use least-privilege IAM, validate input, and derive user identity from verified Cognito claims rather than request data.
- Use `BigDecimal` for monetary values and immutable domain objects where practical.
- Prefer focused changes and meaningful tests. Run the checks for every affected component and report any checks that could not run.
- Use the installed AWS skills when a task involves their service area. Do not run `cdk deploy` unless explicitly requested.
- Protect the user's AWS Free Plan and promotional credits. Do not create or deploy AWS resources, invoke paid services, or otherwise take actions that may consume credits without explicit user authorization. Prefer local tests and `cdk synth`; stop and notify the user before any credit-consuming step.
- Do not make billing, budget, payment, spend-limit, or plan changes. If a task or error involves billing or payments, notify the user and leave the action to them.
- Review changes periodically and at each milestone. Before committing, inspect the diff, run relevant checks, and review the exact staged file list. Never stage credentials, API keys, `.env` files other than `.env.example`, `.venv/`, `venv/`, `node_modules/`, `.pnpm-store/`, `.gradle/`, `build/`, `dist/`, or `cdk.out/`.
- Make small, descriptive commits for each completed and verified milestone, then push those commits to the configured GitHub repository. Stage only files for that milestone; leave other in-progress or unreviewed work untouched.

## AWS experience

- The user uses the new AWS experience. Say “project,” “team member,” and “selected Region”; direct project management, billing, and team-member tasks to AWS Settings.
- Create regional resources only in the project’s selected Region. Do not attempt cross-Region resources or actions, except CloudFront and its required global dependencies in `us-east-1`; Lambda and API Gateway remain in the selected Region.
- Do not use Lambda@Edge or CloudFormation StackSets. Check AWS service availability for the user’s experience before relying on a service.
- If an operation that previously worked suddenly returns AccessDenied, notify the user that project access or limits may be involved. Do not inspect or change billing, budgets, or spend-limit settings.
- Help level: MEDIUM. Execute the user’s request; ask up to two clarifying questions when an ambiguity or potential issue warrants it; do not repeat dismissed questions or explain trade-offs unless asked.
