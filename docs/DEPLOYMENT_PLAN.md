# Deployment Plan

This plan prepares Cubby for its first hosted demo. It is not an authorization to access an AWS account, bootstrap CDK, deploy resources, or upload documents.

## Proposed hosting shape

- Host the existing Vite React app with AWS Amplify Hosting, connected to the GitHub `main` branch. Use Amplify's assigned HTTPS `amplifyapp.com` branch URL for the first demo; a custom domain can be added later if wanted.
- Keep the existing Java 21 Lambda, API Gateway HTTP API, Cognito, DynamoDB, private S3 bucket, and Textract workers in `us-west-2`.
- Keep the backend deploy in CDK. Amplify hosts and builds the frontend; it does not replace or deploy the CDK backend.
- The `main` branch is the hosted demo. A commit to `main` triggers an Amplify frontend build and publish, in addition to the repository's existing validation workflow.

Amplify supports React SPAs, Git-connected deployments, branch URLs and monorepos. Cubby's Amplify monorepo app root is `frontend`; Amplify requires its `AMPLIFY_MONOREPO_APP_ROOT` value to match. The root `amplify.yml` selects Node.js 22.23.3, installs pnpm 10.33.4, runs `pnpm install --frozen-lockfile`, then lint, test and build. It publishes `frontend/dist`. The build runs from the repository root because this is a monorepo build specification, while each pnpm command targets `frontend`. AWS documents this root `buildPath` form and notes that pnpm must be installed in `preBuild`. [Amplify Hosting guide](https://docs.aws.amazon.com/amplify/latest/userguide/), [Amplify monorepo configuration](https://docs.aws.amazon.com/amplify/latest/userguide/monorepo-configuration.html), [Amplify Node.js version guidance](https://docs.aws.amazon.com/amplify/latest/userguide/troubleshooting-general.html).

The React app uses browser routes such as `/receipts/{id}`. In the Amplify console, open **App settings → Rewrites and redirects** and add this single-page-app rule: source `</^[^.]+$|\.(?!(css|gif|ico|jpe?g|js|png|txt|svg|woff|ttf|map)$)([^.]+$)/>`, target `/index.html`, status `200 (Rewrite)`. This lets a reload of a nested route reach React Router without rewriting known static-file types. Keep this rule after any more specific redirects. [Amplify rewrite guidance](https://docs.aws.amazon.com/amplify/latest/userguide/redirects.html).

## CORS values to set after the hosted origin exists

Use one exact HTTPS frontend origin `O`, copied from the deployed Amplify branch URL. An origin includes scheme, host and optional port, with no path. Do not guess the generated Amplify hostname or allow every origin. If a custom domain is added later, add that exact origin deliberately.

For the API Gateway HTTP API, allow origin `O`, methods `GET`, `POST`, `PUT`, and `DELETE`, and headers `Authorization` and `Content-Type`. Do not enable credentialed cookies or expose response headers. Use a 300-second preflight cache. API Gateway handles preflight `OPTIONS` requests when HTTP API CORS is configured. [HTTP API CORS guidance](https://docs.aws.amazon.com/apigateway/latest/developerguide/http-api-cors.html).

For the private receipt bucket, allow origin `O`, method `PUT`, and headers `Content-Type` and `If-None-Match`; expose no headers and use a 300-second preflight cache. The browser sends the Cognito token only to the API; the S3 upload uses the signed URL and signed upload headers. Keep the current private bucket policy and signed URL controls. [S3 CORS guidance](https://docs.aws.amazon.com/AmazonS3/latest/userguide/cors.html).

The origin is passed as the explicit CDK context value `cubby:webOrigin`. When supplied, the stack validates that it is one exact HTTPS origin and applies it to both CORS configurations. Local synthesis without this value remains possible before the Amplify URL exists; it intentionally emits no browser CORS rules. Tests assert the exact configured origin and methods. For a release, pass `cubby:release=true` with the verified `cubby:account` and actual hosted origin. Release synthesis rejects missing account/origin values and reserved placeholder domains; the operator must still compare the origin with the Amplify branch URL. Never synthesize the final deployment template without the actual Amplify branch origin.

## Current status and next action

The local hosting/CORS preparation is committed and pushed as `3611c11`. The owner confirmed that the `cubby` AWS profile resolves to the intended account and that `us-west-2` is the selected Region. This confirms the target only; it does not authorize AWS changes. Before connecting hosting, the coding agent should review and address the build weaknesses in [Build Review](BUILD_REVIEW.md), especially the unresolved CDK account, missing required-origin deployment guard, and missing final frontend runtime-configuration check.

The last local validation passed backend `clean test build`, frontend frozen install/lint/29 tests/build, and infrastructure install/lint/build/13 tests/synth with a sample origin. Amplify itself has not run this build specification. Do not connect Amplify, bootstrap CDK, deploy, or create live AWS resources until the owner separately asks to begin deployment.

When deployment is separately authorized, connect Amplify to `andrewli615/cubby` on `main` with monorepo root `frontend`; verify `AMPLIFY_MONOREPO_APP_ROOT=frontend`; obtain the generated HTTPS branch URL; and set that exact value as `cubby:webOrigin`. Then synthesize and review the account-pinned template and IAM/deployment approach before any bootstrap or deployment. The currently confirmed profile and Region avoid ambiguity about the target; deployment still requires explicit authorization.

## Deployment sequence

1. **Confirmed by the owner:** the `cubby` profile points to the intended account and `us-west-2` is the target Region. Once deployment is separately authorized, inspect whether a Cubby stack or CDK bootstrap resources already exist there before making changes.
2. **Complete locally:** add the Amplify build specification, exact-origin CORS support and tests. Before using it for a release, close the build weaknesses listed in [Build Review](BUILD_REVIEW.md).
3. **Complete locally:** backend clean test/build, frontend frozen install/lint/test/build, and infrastructure lint/build/test/synth. After a hosted origin and account context are configured, rerun synthesis with `cubby:release=true` and review `cdk diff` against the confirmed account/Region before deployment.
4. Connect Amplify Hosting to `andrewli615/cubby`, select the `main` branch and monorepo app root `frontend`, then obtain the actual HTTPS branch origin. Configure the SPA rewrite and Node/pnpm build settings.
5. Add that exact Amplify origin to the CDK CORS input. Review the final synthesized template and infrastructure diff, including retained resources and IAM changes.
6. Review the CDK bootstrap IAM approach before bootstrapping. CDK bootstrap creates publishing/deployment roles and an asset bucket; its default CloudFormation execution policy is broad. Scope the CloudFormation execution role and `iam:PassRole`, and use a permissions boundary where appropriate. Do not accept a default administrator execution policy without reviewing it. [CDK bootstrap resources and permissions](https://docs.aws.amazon.com/cdk/v2/guide/bootstrapping-env.html), [CDK bootstrap customization](https://docs.aws.amazon.com/cdk/v2/guide/bootstrapping-customizing.html).
7. Bootstrap `us-west-2` only after the target identity, role policies and synthesized changes are reviewed. Deploy the CDK stack and record its API endpoint, Cognito User Pool ID and app client ID outputs.
8. Set Amplify build-time variables `CUBBY_RELEASE_BUILD=1`, `VITE_API_BASE_URL`, `VITE_COGNITO_USER_POOL_ID`, and `VITE_COGNITO_CLIENT_ID`, then rebuild and publish the frontend. The release flag makes the build reject missing or malformed runtime configuration. The `VITE_*` values are included in browser code; never put credentials or secrets there.
9. Create the administrator-managed demo user in Cognito, sign in from the hosted origin, and verify sign-in, empty analytics, API preflight, date-filter behavior and a direct reload of a nested route. Keep upload/OCR testing as a separate explicit choice: creating a real receipt starts the existing asynchronous Textract workflow.

## Access and automation

The repository's GitHub Actions workflow currently validates only. It does not have AWS credentials, `id-token: write`, or deployment jobs. Keep it that way for the first deployment unless a separately reviewed GitHub OIDC role and protected `production` environment are implemented. Do not add long-lived AWS keys to GitHub. The existing OIDC design is in [CI/CD](CI_CD.md).

## Decisions for the owner before deployment

- The owner confirmed that `aws sts get-caller-identity --profile cubby` showed the intended AWS account.
- Confirm the generated Amplify HTTPS URL is acceptable for the first hosted demo. A custom domain is optional and not required for the initial release.
- Review the intended role permissions and service availability before provisioning. This planning work has not accessed AWS directly.
- Decide separately whether to authorize deployment and whether to perform a live receipt/OCR smoke test.
