# Development

## Phase 1 checks

- Backend: `cd backend; .\gradlew.bat test build`
- Frontend: `cd frontend; pnpm install; pnpm lint; pnpm test; pnpm build`
- Infrastructure: `cd infrastructure; pnpm install; pnpm build; pnpm synth`

Use Java 21. The frontend and CDK projects use strict TypeScript. Keep generated output (`build/`, `dist/`, `node_modules/`, and `cdk.out/`) out of version control. Do not deploy infrastructure as part of local validation.
