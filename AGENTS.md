# Cubby project instructions

## Current scope

- Follow the phased implementation plan in the project brief. Complete only the phase the user requested; the current baseline is Phase 1 bootstrap.
- The product is a web application. Use React, TypeScript, Vite, Tailwind CSS, and React Router for the frontend; Java 21, Gradle, AWS Lambda, and AWS SDK for Java 2.x for the backend; and AWS CDK with strict TypeScript for infrastructure.
- Do not add Spring Boot, PostgreSQL, Docker services, or production AWS resources. Do not deploy unless the user explicitly asks.
- Keep Lambda handlers thin. Add business logic to services and persistence logic to repositories as later phases introduce them.

## Safety and quality

- Never commit credentials or hard-code secrets. Keep S3 private, use least-privilege IAM, validate input, and derive user identity from verified Cognito claims rather than request data.
- Use `BigDecimal` for monetary values and immutable domain objects where practical.
- Prefer focused changes and meaningful tests. Run the checks for every affected component and report any checks that could not run.
- Use the installed AWS skills when a task involves their service area. Do not run `cdk deploy` unless explicitly requested.

## AWS experience

- The user uses the new AWS experience. Say “project,” “team member,” and “selected Region”; direct project management, billing, and team-member tasks to AWS Settings.
- Create regional resources only in the project’s selected Region. Do not attempt cross-Region resources or actions, except CloudFront and its required global dependencies in `us-east-1`; Lambda and API Gateway remain in the selected Region.
- Do not use Lambda@Edge or CloudFormation StackSets. Check AWS service availability for the user’s experience before relying on a service.
- If an operation that previously worked suddenly returns AccessDenied, ask whether the project has reached its spend limit and direct the user to AWS Settings > Billing.
- Ask whether to keep or clean up successfully created AWS resources when completing a task that created them.
- Help level: MEDIUM. Execute the user’s request; ask up to two clarifying questions when an ambiguity or potential issue warrants it; do not repeat dismissed questions or explain trade-offs unless asked.
