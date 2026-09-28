# Security

Security requirements for later phases:

- Never commit AWS credentials, tokens, or other secrets.
- Keep receipt objects private and use short-lived presigned URLs for uploads.
- Require Cognito JWT authorization for receipt routes and use the verified `sub` claim as the user ID.
- Enforce user-scoped DynamoDB access, validate request data, restrict upload types and size, and avoid exposing internal errors.
- Grant each Lambda only the IAM permissions it needs and encrypt stored data.
- Use GitHub OIDC instead of long-lived AWS access keys for CI/CD.

Phase 1 creates no AWS resources or credentials.
