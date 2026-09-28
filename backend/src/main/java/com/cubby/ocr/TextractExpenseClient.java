package com.cubby.ocr;

import java.util.List;
import java.util.Objects;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.DocumentLocation;
import software.amazon.awssdk.services.textract.model.GetExpenseAnalysisRequest;
import software.amazon.awssdk.services.textract.model.NotificationChannel;
import software.amazon.awssdk.services.textract.model.S3Object;
import software.amazon.awssdk.services.textract.model.StartExpenseAnalysisRequest;

/** AWS SDK v2 adapter. Construction does not invoke Textract. */
public final class TextractExpenseClient implements ExpenseOcrClient {
    private final TextractClient client;

    public TextractExpenseClient(TextractClient client) {
        this.client = Objects.requireNonNull(client);
    }

    @Override
    public String start(String bucket, String key, String clientRequestToken, String receiptId,
            String topicArn, String roleArn) {
        return client.startExpenseAnalysis(StartExpenseAnalysisRequest.builder()
                .documentLocation(DocumentLocation.builder()
                        .s3Object(S3Object.builder().bucket(bucket).name(key).build()).build())
                .clientRequestToken(clientRequestToken).jobTag(receiptId)
                .notificationChannel(NotificationChannel.builder().snsTopicArn(topicArn).roleArn(roleArn).build())
                .build()).jobId();
    }

    @Override
    public Page page(String jobId, String nextToken) {
        var response = client.getExpenseAnalysis(GetExpenseAnalysisRequest.builder()
                .jobId(jobId).nextToken(nextToken).build());
        List<Document> documents = response.expenseDocuments().stream()
                .map(document -> new Document(document.summaryFields().stream()
                        .flatMap(field -> {
                            Field value = new Field(field.type() == null ? "" : field.type().text(),
                                field.valueDetection() == null ? "" : field.valueDetection().text(),
                                field.valueDetection() == null || field.valueDetection().confidence() == null
                                        ? 0 : field.valueDetection().confidence());
                            if (!"TOTAL".equals(value.type()) || field.currency() == null
                                    || field.currency().code() == null) return java.util.stream.Stream.of(value);
                            return java.util.stream.Stream.of(value,
                                    new Field("CURRENCY", field.currency().code(), field.currency().confidence()));
                        })
                        .toList()))
                .toList();
        return new Page(response.jobStatusAsString(), documents, response.nextToken());
    }
}
