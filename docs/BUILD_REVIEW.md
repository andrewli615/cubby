# Build and first-release review

This review reflects `fb494c1` (`Guard Cubby CDK deployment behind verified release inputs`). The local release safeguards are implemented. The repository contains no record of an Amplify or backend deployment.

## Local readiness

- CDK release synthesis requires `cubby:release=true`, a 12-digit `cubby:account`, and an exact HTTPS `cubby:webOrigin`. Plain local synthesis remains account-free and has no browser CORS rule. The guarded deployment command additionally requires `--profile cubby` and verifies the account and Amplify origin before starting CDK; its live checks have not run.
- The initial frontend build can run before backend outputs exist. A build with `CUBBY_RELEASE_BUILD=1` checks the API endpoint and Cognito IDs before compilation and reports variable names without printing their values.
- Node.js 22.23.3 and pnpm 10.33.4 are pinned in `.node-version`, package metadata, GitHub Actions, and `amplify.yml`.
- [GitHub Actions run 36978596668](https://github.com/andrewli615/cubby/actions/runs/36978596668) passed for `fb494c1` on `main` with Java 21, Node 22.23.3, and pnpm 10.33.4. Its backend clean test/build, frontend lint/40 tests/build, and infrastructure lint/build/18 tests/no-lookup synth steps all succeeded. The guarded deploy tests used mocked subprocesses; no deployment or AWS operation was run.
- A Sankey test exceeded its five-second timeout during a concurrent local run, then passed alone and in a complete 40-test rerun. Monitor CI before changing the timeout.

## Remaining release checks

1. **Validate the Amplify build.** After an authorized hosting setup, choose the `frontend` monorepo root and confirm `AMPLIFY_MONOREPO_APP_ROOT=frontend`. Inspect the actual Linux build log, set the documented SPA 200 rewrite, and reload a nested route directly. Local lint, tests, and Vite build do not validate Amplify's service settings or `amplify.yml` interpretation.
2. **Verify the deployment target and CORS origin.** Before any bootstrap or deploy, compare the `cubby` profile with the approved account, use the actual Amplify HTTPS origin, and synthesize/diff with `cubby:release=true`, `cubby:account`, and `cubby:webOrigin`. Review bootstrap IAM and the CloudFormation diff. The `deploy:guarded` entry point checks the non-root STS identity and the Cubby Amplify app/main branch before starting CDK. Direct `cdk deploy` can bypass it and must not be used for the hosted release. These live preflight checks have not run yet.
3. **Validate the configured frontend.** After backend outputs exist, set `CUBBY_RELEASE_BUILD=1` and the three `VITE_*` values in Amplify, then confirm the final build, sign-in, API preflight, empty analytics, and direct route reload. Decide separately whether to perform a live receipt upload and OCR test.
4. **Consider a local build-spec check.** CI runs the frontend and CDK projects but does not parse the root `amplify.yml`. A small YAML structure check would catch accidental edits before Amplify runs the file.

The [deployment plan](DEPLOYMENT_PLAN.md) has the full sequence. AWS documents [CDK environment selection](https://docs.aws.amazon.com/cdk/v2/guide/configure-env.html) and requires Amplify's [monorepo app root](https://docs.aws.amazon.com/amplify/latest/userguide/monorepo-configuration.html) to match `AMPLIFY_MONOREPO_APP_ROOT`.

The guarded entry point was verified with mocked subprocesses: invalid local inputs perform no external calls, and root/wrong-account identities or unmatched Amplify app/branch responses never start CDK. The same infrastructure checks passed locally with Node 24.21.0; the pinned toolchain is now verified by the linked CI run. No guarded deployment command or AWS operation was run.
