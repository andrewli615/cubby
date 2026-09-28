package com.cubby.ocr;

import java.util.List;

/** Small, mockable boundary around Textract's asynchronous expense API. */
public interface ExpenseOcrClient {
    String start(String bucket, String key, String clientRequestToken, String receiptId,
            String topicArn, String roleArn);
    Page page(String jobId, String nextToken);
    record Field(String type, String value, float confidence) {}
    record Document(List<Field> fields) {}
    record Page(String status, List<Document> documents, String nextToken) {}
}
