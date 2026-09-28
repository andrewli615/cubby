# Development

Use Java 21. If JAVA_HOME selects another version, set it to your installed Java 21 directory for the current terminal session. Frontend and CDK projects use strict TypeScript.

## Backend

From the repository root:

```powershell
cd backend
.\gradlew.bat clean test build
```

The build runs local JUnit tests and creates `backend/build/distributions/cubby-lambda.zip`. The ZIP contains the application JAR and runtime dependency JARs under `lib/`, as required by Java Lambda. It includes no test dependencies. The build does not upload the artifact.

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

`pnpm synth` disables context lookups. It uses `cubby:region` from `cdk.json`, currently Oregon (`us-west-2`), and leaves the account unresolved. No AWS credentials or live resources are needed. Tests assert resource inventory, retention, authorization, exact execution-role actions, invocation permission scope, logging fields and missing-configuration failures.

Review `infrastructure/cdk.out/Cubby.template.json` and the assembly manifest after synthesis. The default stage's AutoDeploy property describes future CloudFormation behavior; it does not deploy anything during synthesis. Do not bootstrap or deploy as part of these checks.

The infrastructure consumes the actual Java package and fails if it is missing. Rebuild the backend after Java changes before synthesizing.

## Frontend

```powershell
cd frontend
pnpm install
pnpm lint
pnpm test
pnpm build
```

Keep generated output (`build/`, `dist/`, `node_modules/`, and `cdk.out/`) out of version control. Commit only reviewed source, configuration, tests and documentation.
