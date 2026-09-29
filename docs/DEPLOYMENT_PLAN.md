# Deployment Plan

This plan prepares Cubby for its first hosted demo. It is not an authorization to access an AWS account, bootstrap CDK, deploy resources, or upload documents.

## Proposed hosting shape

- Host the existing Vite React app with AWS Amplify Hosting, connected to the GitHub `main` branch. Use Amplify's assigned HTTPS `amplifyapp.com` branch URL for the first demo; a custom domain can be added later if wanted.
- Keep the existing Java 21 Lambda, API Gateway HTTP API, Cognito, DynamoDB, private S3 bucket, and Textract workers in `us-west-2`.
- Keep the backend deploy in CDK. Amplify hosts and builds the frontend; it does not replace or deploy the CDK backend.
- The `main` branch is the hosted demo. A commit to `main` triggers an Amplify frontend build and publish, in addition to the repository's existing validation workflow.

Amplify supports React SPAs, Git-connected deployments, branch URLs and monorepos. Cubby's Amplify monorepo app root is `frontend`; Amplify requires its `AMPLIFY_MONOREPO_APP_ROOT` value to match. The build should select Node.js 22, install the pinned pnpm version, run `pnpm install --frozen-lockfile`, then lint, test and build. Publish `frontend/dist`. Because the current repo does not have a root pnpm workspace, the build configuration must install pnpm in its pre-build phase and run commands from the frontend app root. [Amplify Hosting guide](https://docs.aws.amazon.com/amplify/latest/userguide/), [Amplify monorepo configuration](https://docs.aws.amazon.com/amplify/latest/userguide/monorepo-configuration.html), [Amplify Node.js version guidance](https://docs.aws.amazon.com/amplify/latest/userguide/troubleshooting-general.html).

The React app uses browser routes such as `/receipts/{id}`. Configure an Amplify SPA rewrite to return `/index.html` for application paths so reloading a nested route does not return a hosting 404.

## CORS values to set after the hosted origin exists

Use one exact HTTPS frontend origin `O`, copied from the deployed Amplify branch URL. An origin includes scheme, host and optional port, with no path. Do not guess the generated Amplify hostname or allow every origin. If a custom domain is added later, add that exact origin deliberately.

For the API Gateway HTTP API, allow origin `O`, methods `GET`, `POST`, `PUT`, and `DELETE`, and headers `Authorization` and `Content-Type`. Do not enable credentialed cookies or expose response headers. Use a 300-second preflight cache. API Gateway handles preflight `OPTIONS` requests when HTTP API CORS is configured. [HTTP API CORS guidance](https://docs.aws.amazon.com/apigateway/latest/developerguide/http-api-cors.html).

For the private receipt bucket, allow origin `O`, method `PUT`, and headers `Content-Type` and `If-None-Match`; expose no headers and use a 300-second preflight cache. The browser sends the Cognito token only to the API; the S3 upload uses the signed URL and signed upload headers. Keep the current private bucket policy and signed URL controls. [S3 CORS guidance](https://docs.aws.amazon.com/AmazonS3/latest/userguide/cors.html).

The CORS origin must be an explicit CDK input so local synth and template tests can assert the exact origin and methods. Add tests for both the HTTP API and S3 bucket before deploying the stack.

## Deployment sequence

1. Confirm the target AWS account and `us-west-2` using the intended `cubby` profile. Check whether the account already contains a Cubby stack or CDK bootstrap resources in that account/Region before making changes.
2. Add the Amplify build specification and SPA rewrite, and make the CDK stack require an explicit hosted frontend origin for both CORS configurations. Add template assertions and document how to set the three Vite build values.
3. Run the local backend clean test/build, frontend install/lint/test/build, and infrastructure lint/build/test/synth. Review `cdk diff` against the chosen account/Region before any deployment.
4. Connect Amplify Hosting to `andrewli615/cubby`, select the `main` branch and monorepo app root `frontend`, then obtain the actual HTTPS branch origin. Configure the SPA rewrite and Node/pnpm build settings.
5. Add that exact Amplify origin to the CDK CORS input. Review the final synthesized template and infrastructure diff, including retained resources and IAM changes.
6. Review the CDK bootstrap IAM approach before bootstrapping. CDK bootstrap creates publishing/deployment roles and an asset bucket; its default CloudFormation execution policy is broad. Scope the CloudFormation execution role and `iam:PassRole`, and use a permissions boundary where appropriate. Do not accept a default administrator execution policy without reviewing it. [CDK bootstrap resources and permissions](https://docs.aws.amazon.com/cdk/v2/guide/bootstrapping-env.html), [CDK bootstrap customization](https://docs.aws.amazon.com/cdk/v2/guide/bootstrapping-customizing.html).
7. Bootstrap `us-west-2` only after the target identity, role policies and synthesized changes are reviewed. Deploy the CDK stack and record its API endpoint, Cognito User Pool ID and app client ID outputs.
8. Set Amplify build-time variables `VITE_API_BASE_URL`, `VITE_COGNITO_USER_POOL_ID`, and `VITE_COGNITO_CLIENT_ID`, then rebuild and publish the frontend. These `VITE_*` values are included in browser code; never put credentials or secrets there.
9. Create the administrator-managed demo user in Cognito, sign in from the hosted origin, and verify sign-in, empty analytics, API preflight, date-filter behavior and a direct reload of a nested route. Keep upload/OCR testing as a separate explicit choice: creating a real receipt starts the existing asynchronous Textract workflow.

## Access and automation

The repository's GitHub Actions workflow currently validates only. It does not have AWS credentials, `id-token: write`, or deployment jobs. Keep it that way for the first deployment unless a separately reviewed GitHub OIDC role and protected `production` environment are implemented. Do not add long-lived AWS keys to GitHub. The existing OIDC design is in [CI/CD](CI_CD.md).

## Decisions for the owner before deployment

- Confirm the AWS account ID shown by `aws sts get-caller-identity --profile cubby` is the intended account.
- Confirm `us-west-2` and the generated Amplify HTTPS URL are acceptable for the first hosted demo. A custom domain is optional and not required for the initial release.
- Review account access and service availability before provisioning. No AWS account has been accessed as part of this plan.
- Decide separately whether to authorize deployment and whether to perform a live receipt/OCR smoke test.
