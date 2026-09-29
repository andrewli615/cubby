# Cubby

Cubby is a company expense tracker for recording expenses from invoices and receipts and exploring where spending goes. Each uploaded document represents one expense with a merchant, date, total, currency, category and OCR review status. Its dashboard summarizes company spending as a Sankey flow from total spending to category to merchant, with each currency shown separately.

Cubby is a focused portfolio demo using React and TypeScript, a Java 21 Lambda backend, Cognito authentication and a user-partitioned DynamoDB table. A single finance user represents the fictional company. OCR-extracted values remain separate from the reviewed expense fields. This MVP does not model invoice schedules, approvals or line items.

## Repository layout

```text
backend/          Java 21 Lambda handlers and JUnit tests
frontend/         React, TypeScript, Vite, Tailwind CSS, and Vitest
infrastructure/   Strict TypeScript AWS CDK application
docs/             Architecture and development references
```

## Prerequisites

- Java 21
- Node.js 20.19+ or 22.12+
- pnpm 10+
- AWS CDK v2 CLI (available through the infrastructure package scripts)

## Local checks

Run backend tests and build:

```powershell
cd backend
.\gradlew.bat test build
```

Run frontend checks:

```powershell
cd frontend
pnpm install
pnpm lint
pnpm test
pnpm build
```

Build the backend first to produce the Lambda ZIP, then validate and synthesize infrastructure:

```powershell
cd infrastructure
pnpm install
pnpm lint
pnpm build
pnpm test
pnpm synth --quiet
```

`cdk synth` writes a CloudFormation template locally. No deployment is performed by these commands.

## Configuration

Copy `.env.example` to `.env.local` when configuring a local API URL. Do not put AWS credentials, tokens, or other secrets in frontend environment variables; Vite exposes `VITE_*` values to browser code.

See [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) for local checks, [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the target architecture, and [docs/AWS_MILESTONES.md](docs/AWS_MILESTONES.md) for implementation progress.
