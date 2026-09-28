package com.cubby.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.cubby.domain.ReceiptValidation;
import com.cubby.auth.CognitoJwtIdentityProvider;
import com.cubby.auth.IdentityProvider;
import com.cubby.repository.DynamoDbReceiptRepository;
import com.cubby.service.DefaultReceiptService;
import com.cubby.service.ReceiptService;
import com.cubby.service.S3ReceiptUploads;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import java.util.Map;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/**
 * Lambda composition root. Receipt identity comes only from API Gateway JWT context.
 */
public final class ReceiptLambdaHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {
    private final ReceiptApiHandler delegate;

    public ReceiptLambdaHandler() {
        this(configuredService(), new CognitoJwtIdentityProvider(
                System.getenv("AWS_REGION"), System.getenv("COGNITO_USER_POOL_ID"),
                System.getenv("COGNITO_CLIENT_ID")));
    }

    ReceiptLambdaHandler(ReceiptService service, IdentityProvider identities) {
        delegate = new ReceiptApiHandler(service, identities);
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        return delegate.handleRequest(event, context);
    }

    private static ReceiptService configuredService() {
        String tableName = System.getenv("RECEIPTS_TABLE_NAME");
        String region = System.getenv("AWS_REGION");
        String bucketName = System.getenv("RECEIPT_IMAGES_BUCKET");
        ReceiptValidation.text(bucketName, "RECEIPT_IMAGES_BUCKET");
        ReceiptValidation.text(tableName, "RECEIPTS_TABLE_NAME");
        ReceiptValidation.text(region, "AWS_REGION");
        // Reused for the lifetime of this Lambda instance. Construction performs no API call.
        DynamoDbClient client = DynamoDbClient.builder().region(Region.of(region)).build();
        S3Presigner presigner = S3Presigner.builder().region(Region.of(region)).build();
        return new DefaultReceiptService(new DynamoDbReceiptRepository(client, tableName),
                new S3ReceiptUploads(presigner, bucketName));
    }
}
