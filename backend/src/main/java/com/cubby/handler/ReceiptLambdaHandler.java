package com.cubby.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.cubby.domain.ReceiptValidation;
import com.cubby.repository.DynamoDbReceiptRepository;
import com.cubby.service.DefaultReceiptService;
import com.cubby.service.ReceiptService;
import java.util.Map;
import java.util.Optional;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/**
 * Lambda composition root. Receipt routes deliberately fail closed until a verified
 * identity provider is installed in the authentication milestone. IAM caller metadata
 * and request-provided identities must never become receipt owners.
 */
public final class ReceiptLambdaHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {
    private final ReceiptApiHandler delegate;

    public ReceiptLambdaHandler() {
        this(configuredService());
    }

    ReceiptLambdaHandler(ReceiptService service) {
        delegate = new ReceiptApiHandler(service, ignored -> Optional.empty());
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        return delegate.handleRequest(event, context);
    }

    private static ReceiptService configuredService() {
        String tableName = System.getenv("RECEIPTS_TABLE_NAME");
        String region = System.getenv("AWS_REGION");
        ReceiptValidation.text(tableName, "RECEIPTS_TABLE_NAME");
        ReceiptValidation.text(region, "AWS_REGION");
        // Reused for the lifetime of this Lambda instance. Construction performs no API call.
        DynamoDbClient client = DynamoDbClient.builder().region(Region.of(region)).build();
        return new DefaultReceiptService(new DynamoDbReceiptRepository(client, tableName));
    }
}
