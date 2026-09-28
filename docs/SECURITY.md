# Security

Security requirements:

- Never commit AWS credentials, tokens, or other secrets.
- Keep receipt objects private and use short-lived presigned URLs for uploads.
- Require Cognito JWT authorization for receipt routes and use the verified `sub` claim as the user ID.
- Enforce user-scoped DynamoDB access, validate request data, restrict upload types and size, and avoid exposing internal errors.
- Grant each Lambda only the IAM permissions it needs and encrypt stored data.
- Use GitHub OIDC instead of long-lived AWS access keys for CI/CD.

Phase 7 defines an administrator-created Cognito User Pool and public SPA client with SRP, token revocation, refresh rotation and optional authenticator MFA. The HTTP API accepts only access tokens with the Cognito sign-in scope on receipt routes; the Lambda derives ownership from API Gateway's verified JWT `sub` claim and checks the configured issuer, client and token type. Browser tokens use tab-scoped session storage. No AWS resources or credentials are created by local validation.
