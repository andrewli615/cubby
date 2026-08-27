---
title: "Cubby - High-Level Engineering Plan"
author: "Project architecture brief"
date: "August 2026"
geometry: margin=0.75in
fontsize: 10pt
header-includes:
  - |
    \usepackage{enumitem}
    \setlist{nosep,leftmargin=*}
---
# 1. Purpose

**Cubby** is a private iPhone-first system for capturing, organizing, and retaining receipts and business transaction documents.

The product should make it easy to:

- scan a physical receipt from an iPhone;
- import or upload electronic receipts, invoices, and supporting documents;
- keep the original document intact;
- extract useful fields such as merchant, date, subtotal, tax, total, and line items;
- organize multiple documents under a single business transaction;
- process work asynchronously using a job queue and workers;
- search and review records later;
- keep an auditable history of document and transaction changes;
- run primarily from a home PC while still using selected AWS services for durability and distributed-systems experience.

Cubby is **not** intended to make tax or deductibility decisions automatically. It stores evidence and structured metadata; the user remains responsible for tax treatment.

# 2. Design Principles

1. **Preserve originals.** Never overwrite the original uploaded receipt, PDF, invoice, or email attachment.
2. **Asynchronous by default.** Expensive work should become background jobs instead of blocking the iPhone app.
3. **Idempotent processing.** A job may run more than once without corrupting or duplicating data.
4. **Private by default.** Do not expose the database or internal services directly to the public internet.
5. **Simple first, scalable later.** Start with a small number of deployable components; do not create unnecessary microservices.
6. **Observable and testable.** Important operations should produce logs, metrics, job states, and audit events.
7. **Canada-first cloud use.** Use AWS Canada Central (`ca-central-1`) for cloud-hosted receipt data and processing where practical.
8. **One source of truth for design.** Keep this Markdown file in the repository and update it as the architecture changes.

# 3. High-Level Architecture

```text
Client
  |
  v
Cubby API
  |
  +-- PostgreSQL
  |
  +-- File Storage
```

## Core deployment decision

The **home PC** runs the long-lived application services:

- Java/Spring Boot API;
- PostgreSQL;
- one or more Dockerized background workers;
- local development and monitoring tools.

AWS provides selected managed services:

- **S3** for durable/off-site document storage;
- **SQS** for asynchronous job delivery;
- **Textract AnalyzeExpense** for optional structured receipt/invoice extraction;
- optional CloudWatch or other AWS services later.

The same worker containers can later be deployed to ECS for portfolio benchmarking without redesigning the application.

# 4. iPhone Capture Layer

## Technology

- **Swift**
- **SwiftUI**
- **VisionKit** document scanner
- optional **Apple Vision** OCR for immediate on-device preview

## Capture flow

```text
Open Cubby
   -> Scan document
   -> VisionKit detects/captures document
   -> User reviews scan
   -> Cubby creates a document record
   -> App uploads original file
   -> UI shows PROCESSING
```

VisionKit should handle document capture and scanner UX. Cubby should **not train a custom model** for corner detection, alignment, or basic OCR in the first version.

Apple Vision may be used for quick local text recognition, but the important custom logic belongs in Cubby's own parser and validation layer.

# 5. Backend and Storage Components

## Spring Boot API

**The Cubby API owns users, document metadata, transactions, processing state, and access to stored files; long-running document processing happens asynchronously through workers.**

This boundary prevents the API from eventually becoming responsible for OCR, reconciliation, exports, and everything else.

### Backend layers

```text
Controller
  |
  v
Service
  |
  v
Repository
  |
  v
PostgreSQL
```

Layer responsibilities:

- **Controller:** HTTP and API concerns only.
- **Service:** business rules.
- **Repository:** database access.
- **Domain model:** Cubby's core concepts.

Responsibilities:

- authenticate requests;
- create and read document records;
- issue upload authorization/presigned URLs;
- expose transaction and search endpoints;
- report processing state to the iPhone app;
- enforce authorization and ownership rules.

### V1 document API

Design the API before implementing it. The initial document surface is:

```text
POST   /documents
GET    /documents
GET    /documents/{id}
DELETE /documents/{id}
```

Deferred document endpoints:

```text
POST /documents/{id}/upload-url
GET  /documents/{id}/download-url
POST /documents/{id}/retry
```

## PostgreSQL

Primary structured-data store for:

- users;
- documents;
- transactions;
- line items;
- payments;
- processing jobs;
- reconciliation results;
- audit events.

For a Document, PostgreSQL stores metadata, not the file itself:

```text
PostgreSQL
  |- filename
  |- owner
  |- status
  |- checksum
  |- timestamps
  `- storage key
```

## Amazon S3

Stores immutable/original document objects such as:

- receipt scans;
- PDF invoices;
- electronic receipts;
- contracts;
- purchase orders;
- payment confirmations;
- generated archive exports.

```text
File storage
  `- actual JPEG, PDF, and HEIC files
```

File storage holds the actual JPEG, PDF, or HEIC bytes. The database stores the storage key and metadata; large binary files belong in object storage.

### Storage abstraction

V1 uses a `DocumentStorage` abstraction backed by `LocalStorage`:

```text
DocumentStorage
  |
  v
LocalStorage
```

Later, `DocumentStorage` can support both local and S3-backed implementations:

```text
DocumentStorage
  |- LocalStorage
  `- S3Storage
```

## Core domain objects

Cubby's core domain objects are **Document**, **User**, and **Transaction**. Implement the **Document** model first; it is the durable record that connects an uploaded file, its owner, and its eventual transaction.

```text
Document
  |- id
  |- owner
  |- original filename
  |- document type
  |- storage location
  |- checksum
  |- processing status
  |- created time
  `- updated time
```

Conceptually, `owner` refers to the User that owns the document, and `storage location` identifies the immutable file in object storage. A Document may later be associated with a Transaction, but document upload and processing must work before that association exists.

### Database relationships

Initial relationship:

```text
USER
 |
 | owns
 v
DOCUMENT
```

Eventually, Documents become part of a transaction-centered model:

```text
USER
 |
 v
TRANSACTION
 |
 +---- DOCUMENT
 |
 +---- PAYMENT
 |
 `---- AUDIT_EVENT
```

### Document lifecycle

```text
CREATED
  |
  v
UPLOADING -----------------> UPLOAD_FAILED
  |
  v
STORED
  |
  v
QUEUED
  |
  v
PROCESSING ----------------> PROCESSING_FAILED
  |
  +------------------------> NEEDS_REVIEW
  |                              |
  v                              v
READY <--------------------------+
```

`UPLOAD_FAILED` records a failed file transfer, `PROCESSING_FAILED` records an unsuccessful asynchronous processing attempt, and `NEEDS_REVIEW` holds documents whose processing results require human confirmation before they become `READY`.

# 6. Job and Queue System

The queue/worker architecture is planned for a later phase and is not part of the initial Document implementation.

```text
Document stored
    |
    v
Processing job created
    |
    v
Queue
    |
    v
Worker
    |
    v
Process document
    |
    v
Update database
```

## Future job model

```text
Job
  |- id
  |- documentId
  |- type
  |- status
  |- attempts
  |- createdAt
  |- startedAt
  `- completedAt
```

## Future job types

- `EXTRACT_TEXT`
- `PARSE_RECEIPT`
- `VALIDATE_RECEIPT`
- `RECONCILE_PAYMENT`
- `GENERATE_EXPORT`

## Job lifecycle

```text
QUEUED -> RUNNING -> SUCCEEDED
                  -> RETRYING -> RUNNING
                  -> FAILED / DEAD_LETTER
```

## Failure handling

- If a database write fails, the API returns a failure response.
- If a file upload fails, the Document remains `UPLOAD_FAILED`.
- If a worker crashes, the queue retries the Job.
- If a Job repeatedly fails, it moves to the dead-letter queue.
- If the same Job runs twice, processing must be idempotent.

## Reliability requirements

Workers should be designed around these assumptions:

- queue messages can be delivered more than once;
- workers can crash midway through a job;
- external services can fail temporarily;
- database writes and message acknowledgements can fail independently.

Therefore Cubby should implement:

- idempotent handlers;
- retry limits;
- exponential backoff with jitter;
- dead-letter queues;
- unique job/document constraints where appropriate;
- explicit job status and attempt counts;
- structured logs and failure reasons.

# 7. Receipt Text Extraction

Cubby should separate **OCR** from **receipt understanding**.

```text
Original document
      |
      v
OCR / extraction engine
      |
      v
raw text + layout + confidence
      |
      v
CUBBY PARSER
      |
      v
CUBBY VALIDATION
      |
      v
normalized receipt / invoice
```

## OCR engines

- **Apple Vision**: optional fast/on-device recognition.
- **Amazon Textract AnalyzeExpense**: structured cloud extraction for receipts and invoices.

## Cubby's own logic

This is the part that should be implemented in the project:

- normalize merchant names;
- identify candidate dates and monetary fields;
- parse line items;
- reconcile subtotal + tax with total;
- use confidence scores;
- detect duplicate documents;
- flag ambiguous records for manual review;
- allow merchant-specific parsing rules if needed.

Do **not** train a custom OCR neural network for v1. A custom OCR model can be an optional experiment later, but it is not part of the core architecture.

# 8. Business Transaction Model

A receipt is only one possible supporting document. The main business entity should be a **Transaction**.

```text
Transaction
  |- receipt
  |- invoice
  |- quote
  |- purchase order
  |- contract
  |- delivery confirmation
  |- payment record
  `- notes / audit events
```

This allows Cubby to support both a small cash receipt and a large business purchase using the same model.

Future transaction features may include:

- document completeness checks;
- invoice/payment matching;
- partial payments;
- three-way matching between purchase order, receipt of goods, and invoice;
- approval states;
- missing-document alerts.

# 9. Security and Integrity

High-level security requirements:

- connect the iPhone to the home server through **Tailscale** rather than exposing internal services directly;
- never expose PostgreSQL publicly;
- use HTTPS for all external traffic;
- encrypt S3 objects at rest;
- block public S3 access;
- use temporary/presigned access for uploads and downloads;
- store secrets outside source code;
- use least-privilege AWS credentials;
- compute and store a SHA-256 checksum for each original document;
- maintain append-only audit events for important actions;
- back up PostgreSQL regularly to an off-site location.

## Future ownership boundaries

```text
User A
  |
  v
can access only User A documents

User B
  |
  v
can access only User B documents
```

Every document query and document action must be scoped to the authenticated owner.

The original document should remain immutable. Any enhanced image used for OCR should be stored as a derived copy.

# 10. Repository Structure

Use a GitHub monorepo.

```text
cubby/
|- ios/                  # Swift / SwiftUI app
|- backend/              # Spring Boot API
|- worker/               # asynchronous Java workers
|- infrastructure/       # Docker + future Terraform/CDK
|- docs/
|  `- CUBBY_PLAN.md      # this document
|- tests/
|- docker-compose.yml
|- README.md
`- .github/workflows/    # CI later
```

Recommended workflow:

```text
GitHub issue
   -> feature branch
   -> implementation + tests
   -> pull request
   -> merge to main
```

Even as a solo project, use pull requests for meaningful changes so architectural and implementation decisions remain visible.

# 11. Implementation Roadmap

## V0.1 - Document metadata foundation

V0.1 is complete when:

- PostgreSQL runs.
- Cubby API runs.
- Document metadata can be created.
- Document metadata can be retrieved.
- Documents can be listed.
- Document metadata can be deleted.
- The architecture is documented.

V0.1 does not include file upload/download, object storage, workers, or document processing.

## Phase 1 - iPhone scanning MVP

Goal: scan a receipt and keep the resulting document on the phone.

- create Cubby SwiftUI project;
- integrate VisionKit;
- scan and preview a receipt;
- save scan metadata locally.

**Exit condition:** a real receipt can be captured reliably on the iPhone.

## Phase 2 - Local backend

Goal: send a receipt from the iPhone to the home PC.

- create Spring Boot API;
- create PostgreSQL schema;
- run both through Docker/Docker Compose where appropriate;
- create/read document records;
- upload and retrieve a document.

**Exit condition:** iPhone -> home server -> persistent document record works end-to-end.

## Phase 3 - Durable object storage

Goal: move original documents to S3 Canada Central.

- introduce S3;
- preserve original objects;
- store object references/checksums in PostgreSQL;
- add off-site database backup.

**Exit condition:** a document survives independently of the iPhone and local filesystem.

## Phase 4 - Queue-driven workers

Goal: convert receipt processing into asynchronous jobs.

- add SQS;
- add Java worker container;
- implement job states;
- add retries, visibility-timeout behavior, idempotency, and dead-letter handling.

**Exit condition:** killing a worker does not lose a receipt-processing job.

## Phase 5 - Extraction and Cubby parser

Goal: turn documents into structured receipt records.

- add Textract and/or Apple Vision OCR;
- build Cubby's parser and validation rules;
- add confidence/manual-review workflow;
- add duplicate detection.

**Exit condition:** common receipts become searchable structured records with user review for ambiguous fields.

## Phase 6 - Business transactions and reconciliation

Goal: support more than individual receipts.

- group documents into Transactions;
- attach invoices, purchase orders, contracts, and payments;
- add reconciliation jobs;
- introduce audit-event history.

**Exit condition:** Cubby can represent and review a complete multi-document business purchase.

## Phase 7 - Portfolio-grade reliability and scale

Goal: demonstrate distributed-systems engineering.

- failure injection;
- load testing;
- queue-depth and latency metrics;
- concurrent workers;
- benchmark home workers vs optional ECS workers;
- CI/CD and infrastructure as code.

**Exit condition:** the README contains measured results, failure scenarios, and architecture tradeoffs rather than only feature screenshots.

# 12. Explicitly Out of Scope for the First Version

Do not start with:

- custom OCR model training;
- Kubernetes/EKS;
- many independent microservices;
- automated tax/deductibility decisions;
- direct bank-account integration;
- public multi-tenant SaaS;
- complex accounting software integrations;
- automated destruction recommendations for paper originals.

These can be revisited only after the core ingestion and job-processing system works.

# 13. First Engineering Milestone

The first meaningful milestone is deliberately small:

```text
GitHub repo
   +
Cubby SwiftUI app
   +
VisionKit document scanner
   +
scan one real receipt
```

After that works, build the system outward one boundary at a time:

```text
iPhone scan
   -> local preview
   -> Spring Boot API
   -> PostgreSQL
   -> S3
   -> SQS
   -> worker
   -> OCR
   -> Cubby parser
   -> transaction reconciliation
```

# 14. Definition of a Strong Portfolio Version

Cubby is portfolio-ready when another SWE can clone the repository, read this plan, understand the architecture, and see evidence that the system handles real engineering concerns.

The final project should demonstrate:

- a working iPhone client;
- a Java/Spring Boot backend;
- relational data modeling;
- object storage;
- queue-based asynchronous processing;
- concurrent workers;
- idempotency and failure recovery;
- retries and dead-letter handling;
- document integrity verification;
- auditability;
- automated tests;
- Dockerized deployment;
- measured throughput/latency/failure behavior;
- clear architectural tradeoffs.

The goal is not maximum feature count. The goal is a small, reliable system whose design choices can be defended in an SWE interview.
