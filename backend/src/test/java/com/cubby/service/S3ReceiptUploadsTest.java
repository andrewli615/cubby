package com.cubby.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.cubby.domain.ReceiptImageKeys;
import com.cubby.dto.UploadUrlRequest;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

class S3ReceiptUploadsTest {
    private static final UUID ID = UUID.fromString("920a995d-693c-4634-9fcf-985b1ddc0199");
    private static final UploadUrlRequest REQUEST = new UploadUrlRequest("receipt.png", "image/png", 123L);
    private final S3Presigner presigner = mock(S3Presigner.class);
    private final ReceiptUploads uploads = new S3ReceiptUploads(presigner, "test-receipt-images", () -> ID);

    @Test
    void signsOnlyAssignedOwnerKeyWithBoundMetadataCreateOnlyHeaderAndFiveMinuteExpiry() throws Exception {
        var signed = mock(PresignedPutObjectRequest.class);
        var expires = Instant.parse("2026-09-28T12:05:00Z");
        var headers = Map.of("content-type", List.of("image/png"), "content-length", List.of("123"),
                "if-none-match", List.of("*"));
        when(signed.url()).thenReturn(URI.create("https://test.example.test/upload").toURL());
        when(signed.expiration()).thenReturn(expires);
        when(signed.signedHeaders()).thenReturn(headers);
        when(presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(signed);
        var response = uploads.createUploadUrl("alice", REQUEST);
        ArgumentCaptor<PutObjectPresignRequest> captured = ArgumentCaptor.captor();
        verify(presigner).presignPutObject(captured.capture());
        var request = captured.getValue();
        assertEquals(300, request.signatureDuration().toSeconds());
        assertEquals("test-receipt-images", request.putObjectRequest().bucket());
        assertEquals("alice/originals/" + ID, request.putObjectRequest().key());
        assertEquals("image/png", request.putObjectRequest().contentType());
        assertEquals(123L, request.putObjectRequest().contentLength());
        assertEquals("*", request.putObjectRequest().ifNoneMatch());
        assertNull(request.putObjectRequest().acl());
        assertTrue(request.putObjectRequest().metadata().isEmpty());
        assertEquals("alice/originals/" + ID, response.imageKey());
        assertEquals(expires, response.expiresAt());
        assertEquals("PUT", response.method());
        assertEquals(headers, response.headers());
        verifyNoMoreInteractions(presigner);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "../bob", "alice/bob", "alice\\bob", "alice%2Fbob", ".", "alice\n"})
    void rejectsInvalidOwnersBeforeSigning(String owner) {
        assertThrows(IllegalArgumentException.class, () -> uploads.createUploadUrl(owner, REQUEST));
        verifyNoInteractions(presigner);
    }

    @Test
    void rejectsMissingMetadataBeforeSigning() {
        assertThrows(IllegalArgumentException.class, () -> uploads.createUploadUrl("alice", null));
        verifyNoInteractions(presigner);
    }

    @Test
    void namespacesDoNotOverlapEvenWhenUploadIdsAreEqual() {
        String alice = ReceiptImageKeys.original("alice", ID);
        String bob = ReceiptImageKeys.original("bob", ID);
        assertNotEquals(alice, bob);
        assertThrows(IllegalArgumentException.class, () -> ReceiptImageKeys.requireOwned("bob", alice));
        assertThrows(IllegalArgumentException.class, () -> ReceiptImageKeys.requireOwned("alice-other", alice));
        ReceiptImageKeys.requireOwned("alice", alice);
    }

    @Test
    void localSdkSigningBindsHeadersAndExpiryWithoutAwsCalls() {
        // Synthetic credentials and explicit Region prevent credential/Region discovery.
        // The presigner performs cryptography locally; this test never executes the URL.
        try (var local = S3Presigner.builder().region(Region.US_WEST_2)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test-access-key", "test-secret-key"))).build()) {
            var realUploads = new S3ReceiptUploads(local, "test-receipt-images");
            Instant before = Instant.now();
            var first = realUploads.createUploadUrl("alice", REQUEST);
            var second = realUploads.createUploadUrl("alice", REQUEST);
            assertNotEquals(first.imageKey(), second.imageKey());
            assertTrue(first.expiresAt().isAfter(before.plusSeconds(298)));
            assertTrue(first.expiresAt().isBefore(Instant.now().plusSeconds(301)));
            assertTrue(first.uploadUrl().getRawQuery().contains("X-Amz-Expires=300"));
            assertFalse(first.uploadUrl().getRawQuery().toLowerCase(java.util.Locale.ROOT).contains("checksum"),
                    "Presigning must not bind an empty-body checksum to a future upload");
            assertEquals(List.of("*"), first.headers().get("if-none-match"));
            assertEquals(List.of("image/png"), first.headers().get("content-type"));
            assertEquals(List.of("123"), first.headers().get("content-length"));
            assertFalse(first.toString().contains("X-Amz"));
        }
    }
}
