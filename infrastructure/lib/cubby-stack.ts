import * as cdk from "aws-cdk-lib";
import * as dynamodb from "aws-cdk-lib/aws-dynamodb";
import * as cognito from "aws-cdk-lib/aws-cognito";
import * as iam from "aws-cdk-lib/aws-iam";
import * as lambda from "aws-cdk-lib/aws-lambda";
import * as logs from "aws-cdk-lib/aws-logs";
import * as s3 from "aws-cdk-lib/aws-s3";
import { AccessLogFormat } from "aws-cdk-lib/aws-apigateway";
import * as apigateway from "aws-cdk-lib/aws-apigatewayv2";
import { HttpJwtAuthorizer } from "aws-cdk-lib/aws-apigatewayv2-authorizers";
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

    const images = new s3.Bucket(this, "ReceiptImages", {
      blockPublicAccess: s3.BlockPublicAccess.BLOCK_ALL,
      objectOwnership: s3.ObjectOwnership.BUCKET_OWNER_ENFORCED,
      encryption: s3.BucketEncryption.S3_MANAGED,
      enforceSSL: true,
      removalPolicy: cdk.RemovalPolicy.RETAIN,
      autoDeleteObjects: false,
    });
    const users = new cognito.UserPool(this, "Users", {
      selfSignUpEnabled: false,
      signInAliases: { email: true },
      autoVerify: { email: true },
      accountRecovery: cognito.AccountRecovery.EMAIL_ONLY,
      mfa: cognito.Mfa.OPTIONAL,
      mfaSecondFactor: { sms: false, otp: true },
      passwordPolicy: { minLength: 12, requireLowercase: true, requireUppercase: true,
        requireDigits: true, requireSymbols: true },
      deletionProtection: true,
      removalPolicy: cdk.RemovalPolicy.RETAIN,
    });
    const webClient = users.addClient("WebClient", {
      generateSecret: false,
      disableOAuth: true,
      authFlows: { userSrp: true },
      preventUserExistenceErrors: true,
      accessTokenValidity: cdk.Duration.minutes(15),
      idTokenValidity: cdk.Duration.minutes(15),
      refreshTokenValidity: cdk.Duration.days(1),
      refreshTokenRotationGracePeriod: cdk.Duration.seconds(30),
      enableTokenRevocation: true,
    });
    const originalsArn = images.arnForObjects("*/originals/*");
    images.addToResourcePolicy(new iam.PolicyStatement({
      sid: "RequireCreateOnlyOriginals",
      effect: iam.Effect.DENY,
      principals: [new iam.AnyPrincipal()],
      actions: ["s3:PutObject"],
      resources: [originalsArn],
      conditions: { StringNotEquals: { "s3:if-none-match": "*" } },
    }));
    images.addToResourcePolicy(new iam.PolicyStatement({
      sid: "RetainOriginals",
      effect: iam.Effect.DENY,
      principals: [new iam.AnyPrincipal()],
      actions: ["s3:DeleteObject", "s3:DeleteObjectVersion"],
      resources: [originalsArn],
    }));
    images.addToResourcePolicy(new iam.PolicyStatement({
      sid: "LimitUploadSignatureAge",
      effect: iam.Effect.DENY,
      principals: [new iam.AnyPrincipal()],
      actions: ["s3:PutObject"],
      resources: [originalsArn],
      conditions: { NumericGreaterThan: { "s3:signatureAge": "300000" } },
    }));

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
      description: "Cubby receipt metadata, create-only image uploads and function logs",
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
      environment: {
        RECEIPTS_TABLE_NAME: receipts.tableName,
        RECEIPT_IMAGES_BUCKET: images.bucketName,
        COGNITO_USER_POOL_ID: users.userPoolId,
        COGNITO_CLIENT_ID: webClient.userPoolClientId,
      },
    });

    // Static SDK analysis identifies these CRUD operations. This single-region table uses
    // DynamoDB-owned encryption: no replication, KMS, Scan, index or table-admin grants.
    receipts.grant(handler,
      "dynamodb:GetItem", "dynamodb:PutItem", "dynamodb:Query", "dynamodb:DeleteItem");
    functionLogs.grants.write(executionRole);
    // Static SDK analysis includes optional ACL/tag/KMS operations; this request uses none.
    executionRole.addToPolicy(new iam.PolicyStatement({
      actions: ["s3:PutObject"],
      resources: [originalsArn],
      conditions: { StringEquals: { "s3:if-none-match": "*" } },
    }));

    const api = new apigateway.HttpApi(this, "HttpApi", {
      createDefaultStage: false,
      defaultAuthorizer: new HttpJwtAuthorizer("ReceiptJwt", users.userPoolProviderUrl, {
        jwtAudience: [webClient.userPoolClientId],
        identitySource: ["$request.header.Authorization"],
      }),
      description: "Cubby API with Cognito access-token authorization for receipt routes",
    });
    const integration = new HttpLambdaIntegration("ReceiptIntegration", handler, {
      payloadFormatVersion: apigateway.PayloadFormatVersion.VERSION_2_0,
    });
    const routes = [
      { path: "/health", method: apigateway.HttpMethod.GET, public: true },
      { path: "/receipts", method: apigateway.HttpMethod.GET, public: false },
      { path: "/receipts", method: apigateway.HttpMethod.POST, public: false },
      { path: "/receipts/upload-url", method: apigateway.HttpMethod.POST, public: false },
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
        ...(!definition.public ? { authorizationScopes: ["aws.cognito.signin.user.admin"] } : {}),
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
    new cdk.CfnOutput(this, "CognitoUserPoolId", { value: users.userPoolId });
    new cdk.CfnOutput(this, "CognitoClientId", { value: webClient.userPoolClientId });
  }
}
