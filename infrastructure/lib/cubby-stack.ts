import * as cdk from "aws-cdk-lib";
import * as dynamodb from "aws-cdk-lib/aws-dynamodb";
import * as iam from "aws-cdk-lib/aws-iam";
import * as lambda from "aws-cdk-lib/aws-lambda";
import * as logs from "aws-cdk-lib/aws-logs";
import { AccessLogFormat } from "aws-cdk-lib/aws-apigateway";
import * as apigateway from "aws-cdk-lib/aws-apigatewayv2";
import { HttpIamAuthorizer } from "aws-cdk-lib/aws-apigatewayv2-authorizers";
import { HttpLambdaIntegration } from "aws-cdk-lib/aws-apigatewayv2-integrations";
import { Construct } from "constructs";
import { existsSync } from "node:fs";
import { fileURLToPath } from "node:url";

export interface CubbyStackProps extends cdk.StackProps {
  readonly lambdaAssetPath?: string;
}

/** Core regional resources only. Synthesis never deploys or performs context lookups. */
export class CubbyStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props: CubbyStackProps) {
    super(scope, id, props);
    if (!props.env?.region || cdk.Token.isUnresolved(this.region)) {
      throw new Error("Set the project's selected Region before synthesizing.");
    }
    const assetPath = props.lambdaAssetPath ?? fileURLToPath(
      new URL("../../backend/build/distributions/cubby-lambda.zip", import.meta.url),
    );
    if (!existsSync(assetPath)) {
      throw new Error("Lambda package is missing. Run the backend Gradle build first.");
    }

    const receipts = new dynamodb.Table(this, "Receipts", {
      partitionKey: { name: "PK", type: dynamodb.AttributeType.STRING },
      sortKey: { name: "SK", type: dynamodb.AttributeType.STRING },
      billingMode: dynamodb.BillingMode.PAY_PER_REQUEST,
      encryption: dynamodb.TableEncryption.DEFAULT,
      pointInTimeRecoverySpecification: { pointInTimeRecoveryEnabled: true },
      deletionProtection: true,
      removalPolicy: cdk.RemovalPolicy.RETAIN,
    });

    const functionLogs = new logs.LogGroup(this, "FunctionLogs", {
      retention: logs.RetentionDays.ONE_MONTH,
      removalPolicy: cdk.RemovalPolicy.RETAIN,
    });
    const accessLogs = new logs.LogGroup(this, "AccessLogs", {
      retention: logs.RetentionDays.ONE_MONTH,
      removalPolicy: cdk.RemovalPolicy.RETAIN,
    });
    const executionRole = new iam.Role(this, "ApiExecutionRole", {
      assumedBy: new iam.ServicePrincipal("lambda.amazonaws.com"),
      description: "Cubby receipt table operations and function log streams only",
    });
    const handler = new lambda.Function(this, "ApiFunction", {
      runtime: lambda.Runtime.JAVA_21,
      architecture: lambda.Architecture.X86_64,
      handler: "com.cubby.handler.ReceiptLambdaHandler::handleRequest",
      code: lambda.Code.fromAsset(assetPath),
      memorySize: 512,
      timeout: cdk.Duration.seconds(15),
      role: executionRole,
      logGroup: functionLogs,
      loggingFormat: lambda.LoggingFormat.JSON,
      environment: { RECEIPTS_TABLE_NAME: receipts.tableName },
    });

    // Static SDK analysis identifies these CRUD operations. This single-region table uses
    // DynamoDB-owned encryption: no replication, KMS, Scan, index or table-admin grants.
    receipts.grant(handler,
      "dynamodb:GetItem", "dynamodb:PutItem", "dynamodb:Query", "dynamodb:DeleteItem");
    functionLogs.grants.write(executionRole);

    const api = new apigateway.HttpApi(this, "HttpApi", {
      createDefaultStage: false,
      defaultAuthorizer: new HttpIamAuthorizer(),
      description: "Cubby core API; receipt identity integration remains disabled",
    });
    const integration = new HttpLambdaIntegration("ReceiptIntegration", handler, {
      payloadFormatVersion: apigateway.PayloadFormatVersion.VERSION_2_0,
    });
    const routes = [
      { path: "/health", method: apigateway.HttpMethod.GET, public: true },
      { path: "/receipts", method: apigateway.HttpMethod.GET, public: false },
      { path: "/receipts", method: apigateway.HttpMethod.POST, public: false },
      { path: "/receipts/{receiptId}", method: apigateway.HttpMethod.GET, public: false },
      { path: "/receipts/{receiptId}", method: apigateway.HttpMethod.PUT, public: false },
      { path: "/receipts/{receiptId}", method: apigateway.HttpMethod.DELETE, public: false },
    ];
    for (const definition of routes) {
      const [route] = api.addRoutes({
        path: definition.path,
        methods: [definition.method],
        integration,
        ...(definition.public ? { authorizer: new apigateway.HttpNoneAuthorizer() } : {}),
      });
      // The pinned integration emits literal path parameters and method/stage wildcards.
      // Its L2 has no permission-ARN option, so refine the generated L1 permission.
      const permission = route?.node.tryFindChild("ReceiptIntegration-Permission");
      if (!(permission instanceof lambda.CfnPermission)) {
        throw new Error("Expected a route-scoped Lambda permission.");
      }
      permission.sourceArn = api.arnForExecuteApi(
        definition.method, definition.path.replace(/\{[^}]+\}/g, "*"), "$default",
      );
      permission.sourceAccount = this.account;
    }
    new apigateway.HttpStage(this, "ApiStage", {
      httpApi: api,
      stageName: "$default",
      autoDeploy: true,
      accessLogSettings: {
        destination: new apigateway.LogGroupLogDestination(accessLogs),
        // Exclude bodies, headers, query strings, caller IPs and authentication claims.
        format: AccessLogFormat.custom(JSON.stringify({
          requestId: "$context.requestId",
          routeKey: "$context.routeKey",
          status: "$context.status",
          responseLength: "$context.responseLength",
          integrationLatency: "$context.integrationLatency",
        })),
      },
    });

    new cdk.CfnOutput(this, "ApiEndpoint", { value: api.apiEndpoint });
    new cdk.CfnOutput(this, "ReceiptsTableName", { value: receipts.tableName });
  }
}
