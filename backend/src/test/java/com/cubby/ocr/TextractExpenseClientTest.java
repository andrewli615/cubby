package com.cubby.ocr;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.*;

class TextractExpenseClientTest {
    private final TextractClient sdk = mock(TextractClient.class);
    private final ExpenseOcrClient client = new TextractExpenseClient(sdk);

    @Test
    void startUsesAsyncExpenseAnalysisWithIdempotencyTokenAndNotificationRole() {
        when(sdk.startExpenseAnalysis(any(StartExpenseAnalysisRequest.class)))
                .thenReturn(StartExpenseAnalysisResponse.builder().jobId("job-1").build());
        assertEquals("job-1", client.start("bucket", "alice/originals/upload", "token", "receipt-id", "topic", "role"));
        var request = ArgumentCaptor.forClass(StartExpenseAnalysisRequest.class);
        verify(sdk).startExpenseAnalysis(request.capture());
        assertEquals("bucket", request.getValue().documentLocation().s3Object().bucket());
        assertEquals("alice/originals/upload", request.getValue().documentLocation().s3Object().name());
        assertEquals("token", request.getValue().clientRequestToken());
        assertEquals("receipt-id", request.getValue().jobTag());
        assertEquals("topic", request.getValue().notificationChannel().snsTopicArn());
        assertEquals("role", request.getValue().notificationChannel().roleArn());
        verifyNoMoreInteractions(sdk);
    }

    @Test
    void pagePassesNextTokenAndMapsExpenseCurrency() {
        when(sdk.getExpenseAnalysis(any(GetExpenseAnalysisRequest.class)))
                .thenReturn(GetExpenseAnalysisResponse.builder().jobStatus(JobStatus.SUCCEEDED).nextToken("next")
                        .expenseDocuments(ExpenseDocument.builder().summaryFields(ExpenseField.builder()
                                .type(ExpenseType.builder().text("TOTAL").build())
                                .valueDetection(ExpenseDetection.builder().text("12.40").confidence(98f).build())
                                .currency(ExpenseCurrency.builder().code("CAD").confidence(99f).build())
                                .build()).build()).build());
        assertEquals(new ExpenseOcrClient.Page("SUCCEEDED", List.of(new ExpenseOcrClient.Document(List.of(
                new ExpenseOcrClient.Field("TOTAL", "12.40", 98f),
                new ExpenseOcrClient.Field("CURRENCY", "CAD", 99f)))), "next"), client.page("job-1", "previous"));
        var request = ArgumentCaptor.forClass(GetExpenseAnalysisRequest.class);
        verify(sdk).getExpenseAnalysis(request.capture());
        assertEquals("job-1", request.getValue().jobId());
        assertEquals("previous", request.getValue().nextToken());
        verifyNoMoreInteractions(sdk);
    }
}
