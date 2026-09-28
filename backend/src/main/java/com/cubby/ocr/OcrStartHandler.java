package com.cubby.ocr;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.cubby.repository.DynamoDbReceiptRepository;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.textract.TextractClient;

/** DynamoDB INSERT arrives only after the uploaded object's receipt record is created. */
public final class OcrStartHandler implements RequestHandler<Map<String, Object>, Void> {
    private final ReceiptOcrService service;

    public OcrStartHandler() {
        Region region = Region.of(System.getenv("AWS_REGION"));
        service = new ReceiptOcrService(new DynamoDbReceiptRepository(
                DynamoDbClient.builder().region(region).build(), System.getenv("RECEIPTS_TABLE_NAME")),
                new TextractExpenseClient(TextractClient.builder().region(region).build()),
                System.getenv("RECEIPT_IMAGES_BUCKET"), System.getenv("TEXTRACT_TOPIC_ARN"),
                System.getenv("TEXTRACT_ROLE_ARN"), Clock.systemUTC());
    }

    public OcrStartHandler(ReceiptOcrService service) { this.service = service; }

    @Override
    public Void handleRequest(Map<String, Object> event, Context context) {
        Object records = event.get("Records");
        if (!(records instanceof List<?> list)) return null;
        for (Object entry : list) {
            if (!(entry instanceof Map<?, ?> record) || !"INSERT".equals(record.get("eventName"))) continue;
            if (!(record.get("dynamodb") instanceof Map<?, ?> dynamodb)
                    || !(dynamodb.get("Keys") instanceof Map<?, ?> keys)) continue;
            String pk = attribute(keys.get("PK"));
            String sk = attribute(keys.get("SK"));
            if (pk == null || sk == null || !pk.startsWith("USER#") || !sk.startsWith("RECEIPT#")) continue;
            UUID receiptId;
            try { receiptId = UUID.fromString(sk.substring(8)); }
            catch (IllegalArgumentException invalid) { continue; }
            service.start(pk.substring(5), receiptId);
        }
        return null;
    }

    private static String attribute(Object value) {
        return value instanceof Map<?, ?> map && map.get("S") instanceof String text ? text : null;
    }
}
