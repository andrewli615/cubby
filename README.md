# Cubby

Cubby is a receipt management web application built with React and TypeScript, a Java 21 backend, and AWS CDK-managed serverless infrastructure. The target architecture uses API Gateway, DynamoDB, private S3 storage, Cognito authentication, and asynchronous Textract processing.

The repository is being built in phases. The current implementation includes the React starter, Java Lambda health and receipt API handlers, receipt domain and DynamoDB repository layers, and an infrastructure scaffold. Authentication, uploads, OCR, and production infrastructure remain future milestones.

## Repository layout

```text
backend/          Java 21 Lambda handlers and JUnit tests
frontend/         React, TypeScript, Vite, Tailwind CSS, and Vitest
infrastructure/   Strict TypeScript AWS CDK application
docs/             Architecture and development references
ios/              Existing SwiftUI prototype, retained from the prior project
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

Build and synthesize the empty infrastructure app:

```powershell
cd infrastructure
pnpm install
pnpm build
pnpm synth
```

`cdk synth` writes a CloudFormation template locally. No deployment is performed by these commands.

## Configuration

Copy `.env.example` to `.env.local` when configuring a local API URL. Do not put AWS credentials, tokens, or other secrets in frontend environment variables; Vite exposes `VITE_*` values to browser code.

See [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) for local checks, [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the target architecture, and [docs/AWS_MILESTONES.md](docs/AWS_MILESTONES.md) for implementation progress.
