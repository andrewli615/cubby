import assert from "node:assert/strict";
import { test } from "node:test";
import * as cdk from "aws-cdk-lib";
import { Match, Template } from "aws-cdk-lib/assertions";
import { CubbyStack } from "../lib/cubby-stack.js";

const app = new cdk.App({ context: { "@aws-cdk/core:checkSecretUsage": true } });
const stack = new CubbyStack(app, "TestCubby", { env: { region: "us-west-2" } });
const template = Template.fromStack(stack);
interface Resource {
  Type: string;
  Properties: Record<string, unknown>;
  DeletionPolicy?: string;
  UpdateReplacePolicy?: string;
}
const resources = (template.toJSON() as { Resources: Record<string, Resource> }).Resources;
const ofType = (type: string) => Object.entries(resources).filter(([, value]) => value.Type === type);
const tableId = ofType("AWS::DynamoDB::Table")[0]![0];
const logIds = ofType("AWS::Logs::LogGroup").map(([id]) => id);
const functionLogId = logIds.find((id) => id.startsWith("FunctionLogs"))!;
const apiId = ofType("AWS::ApiGatewayV2::Api")[0]![0];
const bucketId = ofType("AWS::S3::Bucket")[0]![0];
const userPoolId = ofType("AWS::Cognito::UserPool")[0]![0];
const clientId = ofType("AWS::Cognito::UserPoolClient")[0]![0];
const authorizerId = ofType("AWS::ApiGatewayV2::Authorizer")[0]![0];
const originalsArn = { "Fn::Join": ["", [{ "Fn::GetAtt": [bucketId, "Arn"] }, "/*/originals/*"]] };

test("only the intended core resources are synthesized", () => {
  const expected: Record<string, number> = {
    "AWS::DynamoDB::Table": 1, "AWS::Logs::LogGroup": 4, "AWS::IAM::Role": 4,
    "AWS::IAM::Policy": 4, "AWS::Lambda::Function": 3, "AWS::Lambda::Permission": 9,
    "AWS::Lambda::EventSourceMapping": 1, "AWS::SNS::Topic": 1, "AWS::SNS::Subscription": 1,
    "AWS::ApiGatewayV2::Api": 1, "AWS::ApiGatewayV2::Integration": 1,
    "AWS::ApiGatewayV2::Route": 8, "AWS::ApiGatewayV2::Stage": 1,
    "AWS::S3::Bucket": 1, "AWS::S3::BucketPolicy": 1,
    "AWS::Cognito::UserPool": 1, "AWS::Cognito::UserPoolClient": 1,
    "AWS::ApiGatewayV2::Authorizer": 1,
  };
  assert.deepEqual([...new Set(Object.values(resources).map((value) => value.Type))].sort(),
    Object.keys(expected).sort());
  for (const [type, count] of Object.entries(expected)) template.resourceCountIs(type, count);
});

test("table retains user-partitioned data with recovery and deletion protection", () => {
  template.hasResourceProperties("AWS::DynamoDB::Table", {
    KeySchema: [{ AttributeName: "PK", KeyType: "HASH" }, { AttributeName: "SK", KeyType: "RANGE" }],
    BillingMode: "PAY_PER_REQUEST",
    DeletionProtectionEnabled: true,
    PointInTimeRecoverySpecification: { PointInTimeRecoveryEnabled: true },
    // false selects DynamoDB-owned encryption, not an unencrypted table.
    SSESpecification: { SSEEnabled: false },
    GlobalSecondaryIndexes: Match.absent(),
    StreamSpecification: { StreamViewType: "KEYS_ONLY" },
    ResourcePolicy: Match.absent(),
  });
  assert.equal(resources[tableId]!.DeletionPolicy, "Retain");
  assert.equal(resources[tableId]!.UpdateReplacePolicy, "Retain");
});

test("Java 21 Lambda uses the packaged entry point and stack-local table and logs", () => {
  template.hasResourceProperties("AWS::Lambda::Function", {
    Runtime: "java21",
    Handler: "com.cubby.handler.ReceiptLambdaHandler::handleRequest",
    Environment: { Variables: { RECEIPTS_TABLE_NAME: { Ref: tableId }, RECEIPT_IMAGES_BUCKET: { Ref: bucketId },
      COGNITO_USER_POOL_ID: { Ref: userPoolId }, COGNITO_CLIENT_ID: { Ref: clientId } } },
    LoggingConfig: { LogFormat: "JSON", LogGroup: { Ref: functionLogId } },
    Code: { S3Bucket: { "Fn::Sub": "cdk-hnb659fds-assets-${AWS::AccountId}-us-west-2" },
      S3Key: Match.stringLikeRegexp("\\.zip$") },
    VpcConfig: Match.absent(),
  });
  assert.equal(stack.region, "us-west-2");
});

test("execution role has only receipt table, log-stream and conditional upload operations", () => {
  template.hasResourceProperties("AWS::IAM::Role", {
    AssumeRolePolicyDocument: {
      Version: "2012-10-17",
      Statement: [{ Effect: "Allow", Action: "sts:AssumeRole",
        Principal: { Service: "lambda.amazonaws.com" } }],
    },
    ManagedPolicyArns: Match.absent(),
  });
  template.hasResourceProperties("AWS::IAM::Policy", {
    PolicyDocument: {
      Version: "2012-10-17",
      Statement: [
        { Effect: "Allow", Action: ["dynamodb:GetItem", "dynamodb:PutItem", "dynamodb:Query", "dynamodb:DeleteItem"],
          Resource: [{ "Fn::GetAtt": [tableId, "Arn"] }] },
        { Effect: "Allow", Action: ["logs:CreateLogStream", "logs:PutLogEvents"],
          Resource: { "Fn::GetAtt": [functionLogId, "Arn"] } },
        { Effect: "Allow", Action: "s3:PutObject", Resource: originalsArn,
          Condition: { StringEquals: { "s3:if-none-match": "*" } } },
      ],
    },
  });
});

test("health is the only anonymous route and all seven data routes require scoped JWT access tokens", () => {
  const routes = ofType("AWS::ApiGatewayV2::Route").map(([, value]) => value.Properties);
  assert.deepEqual(routes.filter((route) => route.AuthorizationType === "NONE").map((route) => route.RouteKey),
    ["GET /health"]);
  assert.deepEqual(routes.filter((route) => route.AuthorizationType === "JWT")
    .map((route) => route.RouteKey).sort(),
  ["DELETE /receipts/{receiptId}", "GET /analytics/spending", "GET /receipts", "GET /receipts/{receiptId}",
    "POST /receipts", "POST /receipts/upload-url", "PUT /receipts/{receiptId}"]);
  for (const route of routes.filter((entry) => entry.AuthorizationType === "JWT")) {
    assert.deepEqual(route.AuthorizerId, { Ref: authorizerId });
    assert.deepEqual(route.AuthorizationScopes, ["aws.cognito.signin.user.admin"]);
  }
  template.hasResourceProperties("AWS::ApiGatewayV2::Api", {
    ProtocolType: "HTTP", CorsConfiguration: Match.absent(),
  });
  template.hasResourceProperties("AWS::ApiGatewayV2::Integration", {
    IntegrationType: "AWS_PROXY", PayloadFormatVersion: "2.0",
  });
});

test("Cognito SPA client and API authorizer share the selected pool, client and Region", () => {
  template.hasResourceProperties("AWS::Cognito::UserPool", {
    AdminCreateUserConfig: { AllowAdminCreateUserOnly: true },
    AutoVerifiedAttributes: ["email"],
    DeletionProtection: "ACTIVE",
    MfaConfiguration: "OPTIONAL",
    EnabledMfas: ["SOFTWARE_TOKEN_MFA"],
    UsernameAttributes: ["email"],
  });
  assert.equal(resources[userPoolId]!.DeletionPolicy, "Retain");
  template.hasResourceProperties("AWS::Cognito::UserPoolClient", {
    UserPoolId: { Ref: userPoolId },
    GenerateSecret: false,
    ExplicitAuthFlows: ["ALLOW_USER_SRP_AUTH"],
    PreventUserExistenceErrors: "ENABLED",
    RefreshTokenRotation: { Feature: "ENABLED", RetryGracePeriodSeconds: 30 },
    EnableTokenRevocation: true,
    AllowedOAuthFlowsUserPoolClient: false,
    CallbackURLs: Match.absent(),
  });
  template.hasResourceProperties("AWS::ApiGatewayV2::Authorizer", {
    ApiId: { Ref: apiId },
    AuthorizerType: "JWT",
    IdentitySource: ["$request.header.Authorization"],
    JwtConfiguration: { Audience: [{ Ref: clientId }],
      Issuer: { "Fn::GetAtt": [userPoolId, "ProviderURL"] } },
  });
});

test("Lambda invocations are restricted to this account, API, stage, method and route", () => {
  const suffixes: string[] = [];
  for (const [, resource] of ofType("AWS::Lambda::Permission")
    .filter(([, value]) => value.Properties.Principal === "apigateway.amazonaws.com")) {
    const properties = resource.Properties;
    assert.equal(properties.Principal, "apigateway.amazonaws.com");
    assert.equal(properties.Action, "lambda:InvokeFunction");
    assert.deepEqual(properties.SourceAccount, { Ref: "AWS::AccountId" });
    const arn = properties.SourceArn as { "Fn::Join": [string, unknown[]] };
    assert.ok(arn["Fn::Join"][1].some((part) => JSON.stringify(part) === JSON.stringify({ Ref: apiId })));
    assert.ok(arn["Fn::Join"][1].includes(":execute-api:us-west-2:"));
    suffixes.push(arn["Fn::Join"][1].at(-1) as string);
  }
  assert.deepEqual(suffixes.sort(), [
    "/$default/DELETE/receipts/*", "/$default/GET/analytics/spending", "/$default/GET/health",
    "/$default/GET/receipts", "/$default/GET/receipts/*", "/$default/POST/receipts",
    "/$default/POST/receipts/upload-url", "/$default/PUT/receipts/*",
  ]);
});

test("logs have explicit retention and access logs omit sensitive request data", () => {
  for (const [, resource] of ofType("AWS::Logs::LogGroup")) {
    assert.equal(resource.Properties.RetentionInDays, 30);
    assert.equal(resource.DeletionPolicy, "Retain");
  }
  const stage = ofType("AWS::ApiGatewayV2::Stage")[0]![1];
  const settings = stage.Properties.AccessLogSettings as { Format: string; DestinationArn: unknown };
  assert.deepEqual(Object.keys(JSON.parse(settings.Format) as object).sort(),
    ["integrationLatency", "requestId", "responseLength", "routeKey", "status"]);
  assert.deepEqual(settings.DestinationArn,
    { "Fn::GetAtt": [logIds.find((id) => id.startsWith("AccessLogs")), "Arn"] });
  assert.equal(stage.Properties.StageName, "$default");
});

test("synthesis rejects a missing Region or missing Java package instead of using placeholders", () => {
  assert.throws(() => new CubbyStack(new cdk.App(), "NoRegion", {}), /selected Region/);
  assert.throws(() => new CubbyStack(new cdk.App(), "NoArtifact",
    { env: { region: "us-west-2" }, lambdaAssetPath: "missing-cubby-artifact.zip" }), /Gradle build/);
});

test("receipt bucket is private, encrypted, retained and has no public access or unrelated features", () => {
  template.hasResourceProperties("AWS::S3::Bucket", {
    PublicAccessBlockConfiguration: {
      BlockPublicAcls: true, BlockPublicPolicy: true, IgnorePublicAcls: true, RestrictPublicBuckets: true,
    },
    OwnershipControls: { Rules: [{ ObjectOwnership: "BucketOwnerEnforced" }] },
    BucketEncryption: { ServerSideEncryptionConfiguration: [{ ServerSideEncryptionByDefault: { SSEAlgorithm: "AES256" } }] },
    WebsiteConfiguration: Match.absent(),
    CorsConfiguration: Match.absent(),
    NotificationConfiguration: Match.absent(),
    LifecycleConfiguration: Match.absent(),
  });
  assert.equal(resources[bucketId]!.DeletionPolicy, "Retain");
  assert.equal(resources[bucketId]!.UpdateReplacePolicy, "Retain");
});

test("bucket policy enforces TLS, immutable originals and the upload signature age", () => {
  template.hasResourceProperties("AWS::S3::BucketPolicy", {
    Bucket: { Ref: bucketId },
    PolicyDocument: {
      Version: "2012-10-17",
      Statement: [
        { Effect: "Deny", Principal: { AWS: "*" }, Action: "s3:*",
          Resource: [{ "Fn::GetAtt": [bucketId, "Arn"] },
            { "Fn::Join": ["", [{ "Fn::GetAtt": [bucketId, "Arn"] }, "/*"]] }],
          Condition: { Bool: { "aws:SecureTransport": "false" } } },
        { Sid: "RequireCreateOnlyOriginals", Effect: "Deny", Principal: { AWS: "*" },
          Action: "s3:PutObject", Resource: originalsArn,
          Condition: { StringNotEquals: { "s3:if-none-match": "*" } } },
        { Sid: "RetainOriginals", Effect: "Deny", Principal: { AWS: "*" },
          Action: ["s3:DeleteObject", "s3:DeleteObjectVersion"], Resource: originalsArn },
        { Sid: "LimitUploadSignatureAge", Effect: "Deny", Principal: { AWS: "*" },
          Action: "s3:PutObject", Resource: originalsArn,
          Condition: { NumericGreaterThan: { "s3:signatureAge": "300000" } } },
      ],
    },
  });
});

test("OCR runs from receipt inserts and Textract completion, outside the HTTP request path", () => {
  const functions = ofType("AWS::Lambda::Function");
  const starter = functions.find(([id]) => id.startsWith("OcrStartFunction"))!;
  const completer = functions.find(([id]) => id.startsWith("OcrCompletionFunction"))!;
  assert.equal(starter[1].Properties.Handler, "com.cubby.ocr.OcrStartHandler::handleRequest");
  assert.equal(completer[1].Properties.Handler, "com.cubby.ocr.OcrCompletionHandler::handleRequest");
  const mapping = ofType("AWS::Lambda::EventSourceMapping")[0]![1].Properties;
  assert.deepEqual(mapping.FunctionName, { Ref: starter[0] });
  assert.deepEqual(mapping.EventSourceArn, { "Fn::GetAtt": [tableId, "StreamArn"] });
  assert.deepEqual(mapping.FilterCriteria, { Filters: [{ Pattern: '{"eventName":["INSERT"]}' }] });
  assert.equal(mapping.BatchSize, 1);
  assert.equal(mapping.MaximumRetryAttempts, 10);
  assert.equal(mapping.MaximumRecordAgeInSeconds, 21600);
  const topic = ofType("AWS::SNS::Topic")[0]!;
  assert.equal(topic[1].Properties.TopicName, "AmazonTextract-Cubby");
  assert.deepEqual(ofType("AWS::SNS::Subscription")[0]![1].Properties,
    { Endpoint: { "Fn::GetAtt": [completer[0], "Arn"] }, Protocol: "lambda", TopicArn: { Ref: topic[0] } });
  const snsPermission = ofType("AWS::Lambda::Permission").find(([, value]) =>
    value.Properties.Principal === "sns.amazonaws.com")![1].Properties;
  assert.deepEqual(snsPermission.SourceArn, { Ref: topic[0] });
});

test("OCR IAM separates start, completion and notification permissions", () => {
  const role = (prefix: string) => ofType("AWS::IAM::Role").find(([id]) => id.startsWith(prefix))!;
  const policy = (prefix: string) => ofType("AWS::IAM::Policy").find(([id]) => id.startsWith(prefix))!;
  const start = policy("OcrStartRole")[1].Properties.PolicyDocument as { Statement: Array<Record<string, unknown>> };
  const finish = policy("OcrCompletionRole")[1].Properties.PolicyDocument as { Statement: Array<Record<string, unknown>> };
  const notify = policy("TextractNotificationRole")[1].Properties.PolicyDocument as { Statement: Array<Record<string, unknown>> };
  const topicId = ofType("AWS::SNS::Topic")[0]![0];
  const notificationRoleId = role("TextractNotificationRole")[0];
  assert.deepEqual(notify.Statement, [{ Action: "sns:Publish", Effect: "Allow", Resource: { Ref: topicId } }]);
  assert.deepEqual(start.Statement.map((statement) => statement.Action), [
    "dynamodb:ListStreams", ["dynamodb:DescribeStream", "dynamodb:GetRecords", "dynamodb:GetShardIterator"],
    ["dynamodb:GetItem", "dynamodb:UpdateItem"], ["logs:CreateLogStream", "logs:PutLogEvents"],
    "textract:StartExpenseAnalysis", "iam:PassRole", "s3:GetObject",
  ]);
  assert.deepEqual(start.Statement[1]!.Resource, { "Fn::GetAtt": [tableId, "StreamArn"] });
  assert.equal(start.Statement[0]!.Resource, "*"); // ListStreams has no resource ARN.
  assert.deepEqual(start.Statement[2]!.Resource, [{ "Fn::GetAtt": [tableId, "Arn"] }]);
  assert.deepEqual(start.Statement[4]!.Resource, "*"); // Textract jobs have no IAM resource type.
  assert.deepEqual(start.Statement[5]!.Resource, { "Fn::GetAtt": [notificationRoleId, "Arn"] });
  assert.deepEqual(start.Statement[5]!.Condition,
    { StringEquals: { "iam:PassedToService": "textract.amazonaws.com" } });
  assert.deepEqual(start.Statement[6]!.Resource, originalsArn);
  assert.deepEqual(finish.Statement.map((statement) => statement.Action), [
    ["dynamodb:GetItem", "dynamodb:UpdateItem"], ["logs:CreateLogStream", "logs:PutLogEvents"],
    "textract:GetExpenseAnalysis",
  ]);
  assert.deepEqual(finish.Statement[0]!.Resource, [{ "Fn::GetAtt": [tableId, "Arn"] }]);
  assert.deepEqual(finish.Statement[2]!.Resource, "*");
  const trust = role("TextractNotificationRole")[1].Properties.AssumeRolePolicyDocument as {
    Statement: Array<Record<string, unknown>> };
  assert.deepEqual(trust.Statement[0]!.Principal, { Service: "textract.amazonaws.com" });
  assert.deepEqual(trust.Statement[0]!.Condition, {
    ArnLike: { "aws:SourceArn": { "Fn::Join": ["", ["arn:", { Ref: "AWS::Partition" },
      ":textract:*:", { Ref: "AWS::AccountId" }, ":*"]] } },
    StringEquals: { "aws:SourceAccount": { Ref: "AWS::AccountId" } },
  });
});
