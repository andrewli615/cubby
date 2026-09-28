package com.cubby.repository;

import com.cubby.domain.Receipt;
import com.cubby.domain.OcrMetadata;
import com.cubby.domain.ReceiptStatus;
import com.cubby.domain.ReceiptValidation;
import java.util.ArrayList;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

/**
 * Uses an injected client and table name; never creates clients, credentials or resources.
 * The caller owns the client's lifecycle. SDK failures other than write conflicts propagate.
 */
public final class DynamoDbReceiptRepository implements ReceiptRepository {
    private final DynamoDbClient client;
    private final String tableName;

    public DynamoDbReceiptRepository(DynamoDbClient client, String tableName) {
        ReceiptValidation.required(client, "client");
        ReceiptValidation.text(tableName, "tableName");
        this.client = client;
        this.tableName = tableName;
    }

    @Override
    public Receipt save(String userId, Receipt receipt) {
        requireOwner(userId, receipt);
        try {
            client.putItem(PutItemRequest.builder().tableName(tableName)
                    .item(ReceiptItemMapper.toItem(receipt))
                    .conditionExpression("attribute_not_exists(#pk) AND attribute_not_exists(#sk)")
                    .expressionAttributeNames(Map.of("#pk", "PK", "#sk", "SK")).build());
            return receipt;
        } catch (ConditionalCheckFailedException exception) {
            throw new ReceiptWriteConflictException("Receipt already exists for this user", exception);
        }
    }

    @Override
    public Optional<Receipt> find(String userId, UUID receiptId) {
        var response = client.getItem(GetItemRequest.builder().tableName(tableName)
                .key(ReceiptItemMapper.key(userId, receiptId)).consistentRead(true).build());
        if (!response.hasItem() || response.item().isEmpty()) {
            return Optional.empty();
        }
        Receipt receipt = ReceiptItemMapper.fromItem(response.item(), userId);
        if (!receiptId.equals(receipt.receiptId())) {
            throw new IllegalStateException("Receipt item does not match the requested receipt ID");
        }
        return Optional.of(receipt);
    }

    @Override
    public List<Receipt> listByUser(String userId) {
        ReceiptValidation.text(userId, "userId");
        List<Receipt> receipts = new ArrayList<>();
        Map<String, AttributeValue> nextKey = Map.of();
        do {
            QueryResponse response = client.query(QueryRequest.builder().tableName(tableName)
                    .keyConditionExpression("#pk = :user AND begins_with(#sk, :receipt)")
                    .expressionAttributeNames(Map.of("#pk", "PK", "#sk", "SK"))
                    .expressionAttributeValues(Map.of(
                            ":user", ReceiptItemMapper.string("USER#" + userId),
                            ":receipt", ReceiptItemMapper.string("RECEIPT#")))
                    .exclusiveStartKey(nextKey).consistentRead(true).build());
            for (var item : response.items()) {
                receipts.add(ReceiptItemMapper.fromItem(item, userId));
            }
            nextKey = response.lastEvaluatedKey();
        } while (nextKey != null && !nextKey.isEmpty());
        return List.copyOf(receipts);
    }

    @Override
    public Receipt update(String userId, Receipt receipt) {
        requireOwner(userId, receipt);
        Map<String, AttributeValue> item = ReceiptItemMapper.toItem(receipt);
        Map<String, AttributeValue> conditions = new HashMap<>(Map.of(
                ":created", item.get("createdAt"), ":image", item.get("imageKey"),
                ":status", item.get("status")));
        String jobCondition = " AND attribute_not_exists(#job)";
        if (receipt.ocrJobId() != null) {
            conditions.put(":job", ReceiptItemMapper.string(receipt.ocrJobId()));
            jobCondition = " AND #job = :job";
        }
        try {
            client.putItem(PutItemRequest.builder().tableName(tableName).item(item)
                    .conditionExpression("attribute_exists(#pk) AND attribute_exists(#sk)"
                            + " AND #created = :created AND #image = :image"
                            + " AND #status = :status" + jobCondition)
                    .expressionAttributeNames(Map.of("#pk", "PK", "#sk", "SK",
                            "#created", "createdAt", "#image", "imageKey",
                            "#status", "status", "#job", "ocrJobId"))
                    .expressionAttributeValues(conditions).build());
            return receipt;
        } catch (ConditionalCheckFailedException exception) {
            throw new ReceiptWriteConflictException(
                    "Receipt is missing or its immutable fields do not match", exception);
        }
    }

    @Override
    public boolean transitionOcr(String userId, UUID receiptId, String imageKey,
            ReceiptStatus from, String expectedJobId, ReceiptStatus to,
            String jobId, OcrMetadata metadata, Instant updatedAt) {
        ReceiptValidation.text(imageKey, "imageKey");
        ReceiptValidation.required(from, "from");
        ReceiptValidation.required(to, "to");
        ReceiptValidation.required(updatedAt, "updatedAt");
        Map<String, String> names = new HashMap<>(Map.of("#pk", "PK", "#sk", "SK", "#image", "imageKey",
                "#status", "status", "#job", "ocrJobId", "#updated", "updatedAt"));
        Map<String, AttributeValue> values = new HashMap<>(Map.of(
                ":image", ReceiptItemMapper.string(imageKey),
                ":from", ReceiptItemMapper.string(from.name()),
                ":to", ReceiptItemMapper.string(to.name()),
                ":updated", ReceiptItemMapper.string(updatedAt.toString())));
        String condition = "attribute_exists(#pk) AND attribute_exists(#sk)"
                + " AND #image = :image AND #status = :from";
        if (expectedJobId == null) condition += " AND attribute_not_exists(#job)";
        else {
            condition += " AND #job = :expectedJob";
            values.put(":expectedJob", ReceiptItemMapper.string(expectedJobId));
        }
        String update = "SET #status = :to, #updated = :updated";
        if (jobId != null) {
            update += ", #job = :job";
            values.put(":job", ReceiptItemMapper.string(jobId));
        }
        if (metadata != null) {
            update += ", #ocr = :ocr";
            names.put("#ocr", "ocr");
            values.put(":ocr", ReceiptItemMapper.ocrValue(metadata));
        }
        try {
            client.updateItem(UpdateItemRequest.builder().tableName(tableName)
                    .key(ReceiptItemMapper.key(userId, receiptId))
                    .conditionExpression(condition).updateExpression(update)
                    .expressionAttributeNames(names).expressionAttributeValues(values).build());
            return true;
        } catch (ConditionalCheckFailedException exception) {
            return false;
        }
    }

    @Override
    public boolean delete(String userId, UUID receiptId) {
        var response = client.deleteItem(DeleteItemRequest.builder().tableName(tableName)
                .key(ReceiptItemMapper.key(userId, receiptId)).returnValues(ReturnValue.ALL_OLD).build());
        return response.hasAttributes() && !response.attributes().isEmpty();
    }

    private static void requireOwner(String userId, Receipt receipt) {
        ReceiptValidation.text(userId, "userId");
        ReceiptValidation.required(receipt, "receipt");
        if (!userId.equals(receipt.userId())) {
            throw new IllegalArgumentException("Receipt owner must match the authenticated user");
        }
    }
}
