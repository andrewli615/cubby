# Build and first-release review

This review reflects `5613db7` (`Guard release configuration and pin build tools`). The local release safeguards are implemented. The repository contains no record of an Amplify or backend deployment.

## Local readiness

- CDK release synthesis requires `cubby:release=true`, a 12-digit `cubby:account`, and an exact HTTPS `cubby:webOrigin`. Plain local synthesis remains account-free and has no browser CORS rule. The release check rejects missing values and some placeholder hostnames; the operator must still compare the origin with the real Amplify URL.
- The initial frontend build can run before backend outputs exist. A build with `CUBBY_RELEASE_BUILD=1` checks the API endpoint and Cognito IDs before compilation and reports variable names without printing their values.
- Node.js 22.23.3 and pnpm 10.33.4 are pinned in `.node-version`, package metadata, GitHub Actions, and `amplify.yml`.
- Java 21 backend `clean test build` passed. On the available local Node 24/pnpm 11 runtime, frontend lint, 40 tests, and build passed; infrastructure lint, build, 14 tests, and no-lookup synth passed in both discovery and synthetic release modes. The pinned Node/pnpm combination still needs confirmation from CI or an equivalent local environment.
- A Sankey test exceeded its five-second timeout during a concurrent local run, then passed alone and in a complete 40-test rerun. Monitor CI before changing the timeout.

## Remaining release checks

1. **Verify CI with the pinned tools.** Confirm a `main` validation run containing `5613db7` passed on Node.js 22.23.3 and pnpm 10.33.4. The local checks above used different installed versions.
2. **Validate the Amplify build.** After an authorized hosting setup, choose the `frontend` monorepo root and confirm `AMPLIFY_MONOREPO_APP_ROOT=frontend`. Inspect the actual Linux build log, set the documented SPA 200 rewrite, and reload a nested route directly. Local lint, tests, and Vite build do not validate Amplify's service settings or `amplify.yml` interpretation.
3. **Verify the deployment target and CORS origin.** Before any bootstrap or deploy, compare `aws sts get-caller-identity --profile cubby` with the approved account, use the actual Amplify HTTPS origin, and synthesize/diff with `cubby:release=true`, `cubby:account`, and `cubby:webOrigin`. Review bootstrap IAM and the CloudFormation diff. The release check is opt-in: a direct `cdk deploy` without `cubby:release=true` can bypass it. Add a guarded deployment command or require an exact reviewed invocation before provisioning.
4. **Validate the configured frontend.** After backend outputs exist, set `CUBBY_RELEASE_BUILD=1` and the three `VITE_*` values in Amplify, then confirm the final build, sign-in, API preflight, empty analytics, and direct route reload. Decide separately whether to perform a live receipt upload and OCR test.
5. **Consider a local build-spec check.** CI runs the frontend and CDK projects but does not parse the root `amplify.yml`. A small YAML structure check would catch accidental edits before Amplify runs the file.

The [deployment plan](DEPLOYMENT_PLAN.md) has the full sequence. AWS documents [CDK environment selection](https://docs.aws.amazon.com/cdk/v2/guide/configure-env.html) and requires Amplify's [monorepo app root](https://docs.aws.amazon.com/amplify/latest/userguide/monorepo-configuration.html) to match `AMPLIFY_MONOREPO_APP_ROOT`.
