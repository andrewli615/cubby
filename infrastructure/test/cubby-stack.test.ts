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

test("only the intended core resources are synthesized", () => {
  const expected: Record<string, number> = {
    "AWS::DynamoDB::Table": 1, "AWS::Logs::LogGroup": 2, "AWS::IAM::Role": 1,
    "AWS::IAM::Policy": 1, "AWS::Lambda::Function": 1, "AWS::Lambda::Permission": 6,
    "AWS::ApiGatewayV2::Api": 1, "AWS::ApiGatewayV2::Integration": 1,
    "AWS::ApiGatewayV2::Route": 6, "AWS::ApiGatewayV2::Stage": 1,
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
    StreamSpecification: Match.absent(),
    ResourcePolicy: Match.absent(),
  });
  assert.equal(resources[tableId]!.DeletionPolicy, "Retain");
  assert.equal(resources[tableId]!.UpdateReplacePolicy, "Retain");
});

test("Java 21 Lambda uses the packaged entry point and stack-local table and logs", () => {
  template.hasResourceProperties("AWS::Lambda::Function", {
    Runtime: "java21",
    Handler: "com.cubby.handler.ReceiptLambdaHandler::handleRequest",
    Environment: { Variables: { RECEIPTS_TABLE_NAME: { Ref: tableId } } },
    LoggingConfig: { LogFormat: "JSON", LogGroup: { Ref: functionLogId } },
    Code: { S3Bucket: { "Fn::Sub": "cdk-hnb659fds-assets-${AWS::AccountId}-us-west-2" },
      S3Key: Match.stringLikeRegexp("\\.zip$") },
    VpcConfig: Match.absent(),
  });
  assert.equal(stack.region, "us-west-2");
});

test("execution role has only four table operations and two log-stream operations", () => {
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
      ],
    },
  });
});

test("health is the only anonymous route and all five receipt routes require IAM", () => {
  const routes = ofType("AWS::ApiGatewayV2::Route").map(([, value]) => value.Properties);
  assert.deepEqual(routes.filter((route) => route.AuthorizationType === "NONE").map((route) => route.RouteKey),
    ["GET /health"]);
  assert.deepEqual(routes.filter((route) => route.AuthorizationType === "AWS_IAM")
    .map((route) => route.RouteKey).sort(),
  ["DELETE /receipts/{receiptId}", "GET /receipts", "GET /receipts/{receiptId}",
    "POST /receipts", "PUT /receipts/{receiptId}"]);
  template.hasResourceProperties("AWS::ApiGatewayV2::Api", {
    ProtocolType: "HTTP", CorsConfiguration: Match.absent(),
  });
  template.hasResourceProperties("AWS::ApiGatewayV2::Integration", {
    IntegrationType: "AWS_PROXY", PayloadFormatVersion: "2.0",
  });
});

test("Lambda invocations are restricted to this account, API, stage, method and route", () => {
  const suffixes: string[] = [];
  for (const [, resource] of ofType("AWS::Lambda::Permission")) {
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
    "/$default/DELETE/receipts/*", "/$default/GET/health", "/$default/GET/receipts",
    "/$default/GET/receipts/*", "/$default/POST/receipts", "/$default/PUT/receipts/*",
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
