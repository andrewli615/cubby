package com.cubby.repository;

import com.cubby.domain.Receipt;
import com.cubby.domain.ReceiptValidation;
import java.util.ArrayList;
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
        Map<String, AttributeValue> conditions = Map.of(
                ":created", item.get("createdAt"), ":image", item.get("imageKey"));
        try {
            client.putItem(PutItemRequest.builder().tableName(tableName).item(item)
                    .conditionExpression("attribute_exists(#pk) AND attribute_exists(#sk)"
                            + " AND #created = :created AND #image = :image")
                    .expressionAttributeNames(Map.of("#pk", "PK", "#sk", "SK",
                            "#created", "createdAt", "#image", "imageKey"))
                    .expressionAttributeValues(conditions).build());
            return receipt;
        } catch (ConditionalCheckFailedException exception) {
            throw new ReceiptWriteConflictException(
                    "Receipt is missing or its immutable fields do not match", exception);
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
