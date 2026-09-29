# Development

Use Java 21. If JAVA_HOME selects another version, set it to your installed Java 21 directory for the current terminal session. Use Node.js 22.23.3 and pnpm 10.33.4 for frontend and infrastructure work; these versions are pinned in `.node-version`, the package manifests, GitHub Actions and Amplify. Frontend and CDK projects use strict TypeScript.

## Backend

From the repository root:

```powershell
cd backend
.\gradlew.bat clean test build
```

The build runs local JUnit tests and creates `backend/build/distributions/cubby-lambda.zip`. The ZIP contains the application JAR and runtime dependency JARs under `lib/`, as required by Java Lambda. It includes no test dependencies. The build does not upload the artifact. Upload unit tests mock `S3Presigner`; one additional signature contract test uses synthetic credentials and an explicit Region for local cryptography only. No test executes a presigned URL.

## Infrastructure

Build the backend first. From the repository root:

```powershell
cd infrastructure
pnpm install --frozen-lockfile
pnpm lint
pnpm build
pnpm test
pnpm synth --quiet
```

`pnpm synth` disables context lookups. It uses `cubby:region` from `cdk.json`, currently Oregon (`us-west-2`), and leaves the account unresolved. No AWS credentials or live resources are needed. Tests assert resource inventory, retention, Cognito/JWT authorization, exact execution-role actions, invocation permission scope, logging fields, the private S3 bucket, conditional upload permissions, immutable originals and missing-configuration failures.

For a configured release synthesis, pass `--context cubby:release=true`, the verified 12-digit `cubby:account`, and the actual HTTPS `cubby:webOrigin`. This pins the stack to the target account and rejects missing values and reserved placeholder domains. Compare the account with `aws sts get-caller-identity` for the selected non-root profile before `cdk diff` or deployment; verify the origin against the Amplify branch URL and never use a test origin for those operations. Plain local synthesis remains account-free.

Review `infrastructure/cdk.out/Cubby.template.json` and the assembly manifest after synthesis. The default stage's AutoDeploy property describes future CloudFormation behavior; it does not deploy anything during synthesis. Do not bootstrap or deploy as part of these checks.

The infrastructure consumes the actual Java package and fails if it is missing. Rebuild the backend after Java changes before synthesizing.

## Frontend

```powershell
cd frontend
pnpm install --frozen-lockfile
pnpm lint
pnpm test
pnpm build
```

To preview the dashboard before Cognito or API deployment, run `pnpm dev` and open `http://localhost:5173/?demo=1`. The preview is enabled only in Vite development mode on localhost, uses synthetic CAD/USD data in memory, and makes no network requests. Refreshing restores the sample dataset. Without the `demo=1` query, the normal Cognito configuration and sign-in flow is unchanged.

Keep generated output (`build/`, `dist/`, `node_modules/`, and `cdk.out/`) out of version control. Commit only reviewed source, configuration, tests and documentation.

The initial Amplify build may run without backend outputs so its branch URL can be discovered. The final release build must set `CUBBY_RELEASE_BUILD=1` alongside `VITE_API_BASE_URL`, `VITE_COGNITO_USER_POOL_ID`, and `VITE_COGNITO_CLIENT_ID`. The frontend build checks these values before compiling and reports variable names only when they are missing or malformed.

For local sign-in, copy the repository `.env.example` values into `frontend/.env.local` and set `VITE_COGNITO_USER_POOL_ID` and `VITE_COGNITO_CLIENT_ID` to the stack outputs after a separately authorized deployment. These IDs are public configuration, not secrets. The browser client uses SRP sign-in and tab-scoped session storage; administrator-created users can set their first password, and enrolled users can answer a TOTP challenge. Self-sign-up is disabled. Phase 7 does not create users or identity resources.
