# Build and first-release review

This review covers the repository at `3611c11` (`Prepare Cubby Amplify hosting and CORS`). It distinguishes passing local checks from issues that still need to be closed before the first hosted release. It does not authorize AWS access or deployment.

## Validation evidence

- Backend: Java 21 `gradlew clean test build` passed and produced the Lambda ZIP.
- Frontend: frozen pnpm install, lint, 29 tests, and production build passed. One Sankey test exceeded the default five-second timeout during a concurrent local run; an isolated rerun passed all 29 tests in 3.73 seconds. Treat it as an observed load-sensitive test and watch CI for recurrence before changing the timeout.
- Infrastructure: frozen pnpm install, lint, TypeScript build, all 13 CDK tests, and synth with a documentation-only test origin passed. The synthesized API and bucket CORS values matched the exact test origin.
- Amplify: the build has not yet run in Amplify's Linux build image. Local checks do not validate its build-spec interpretation, Git integration, or branch settings.

## Gaps to close before a hosted release

1. **Pin the CDK target account and require the hosted origin.** `infrastructure/bin/cubby.ts` currently supplies a Region but no account, so the synthesized stack is account-agnostic. CDK can deploy an environment-agnostic stack using `--profile`; the risk is that the selected profile determines the destination. Add a deployment-only configuration path that requires a 12-digit account and exact HTTPS origin while preserving account-free local synth. Before deployment, use `--profile cubby`, pass or verify the confirmed account against `aws sts get-caller-identity`, and review the resulting `cdk diff`. Never use the sample test origin for deployment.
2. **Fail the final frontend release build when runtime configuration is missing.** `auth.ts` leaves Cognito unconfigured when either Cognito value is absent, and `receipts-api.ts` reports a missing API URL only when a request is made. Therefore the current Amplify build can succeed and publish a page that cannot sign in or reach the API. Keep the first hosting build permissive so the Amplify branch URL can be obtained; add a final-release check for `VITE_API_BASE_URL`, `VITE_COGNITO_USER_POOL_ID`, and `VITE_COGNITO_CLIENT_ID` after backend outputs exist. The check must not print values, and no AWS credentials belong in `VITE_*` variables.
3. **Align the Node and pnpm versions.** The README permits Node 20/22, GitHub Actions uses Node 24, Amplify uses Node 22, and local validation used pnpm 11.19.0 while CI and Amplify install an unspecified pnpm 10 release. Pick a supported Node 22 patch and one exact pnpm 10 release, declare them in the relevant package metadata/toolchain file, and use the same values in CI and Amplify. Rerun all three project builds after the change.
4. **Verify Amplify's monorepo settings in its first authorized build.** The build spec declares `appRoot: frontend`; the Amplify app must use `AMPLIFY_MONOREPO_APP_ROOT=frontend`. Selecting the monorepo and app root in the Amplify console sets this automatically for a console-created app. Confirm the value and review the build logs. Add the documented SPA 200 rewrite and test a direct reload of `/receipts/{id}`.
5. **Add a build-spec check to local/CI validation.** The current CI does not parse or execute `amplify.yml`. Keep AWS access out of CI; add a local schema/structure check if a suitable parser is already available, then use the first authorized Amplify build as the integration test for the service-specific spec.

The CDK stack's environment-agnostic behavior is supported, but its deployment target must be explicit in the release workflow. AWS documents profile-based deployment for environment-agnostic stacks and requires Amplify's monorepo `appRoot` and `AMPLIFY_MONOREPO_APP_ROOT` values to match. See [CDK environment configuration](https://docs.aws.amazon.com/cdk/v2/guide/configure-env.html), [Amplify monorepo configuration](https://docs.aws.amazon.com/amplify/latest/userguide/monorepo-configuration.html), and [Amplify Node.js version guidance](https://docs.aws.amazon.com/amplify/latest/userguide/troubleshooting-general.html).

## Agent sequence

1. Implement items 1–3 as a local code/build milestone; add tests for rejected missing/invalid deployment inputs and ensure plain local `cdk synth` still works without an account or hosted origin.
2. Run the complete backend, frontend, and infrastructure checks from `docs/DEVELOPMENT.md`; inspect the configured synth using a clearly labeled test origin and verify no placeholder can enter deployment configuration.
3. Review the diff and exact staged files, scan for credentials and generated output, then make a small commit and push it. Update this review with the actual toolchain versions and results.
4. Stop before creating the Amplify app, bootstrapping, deploying, creating a Cognito user, or uploading a receipt. Those are separate AWS actions requiring the owner's explicit go-ahead.
