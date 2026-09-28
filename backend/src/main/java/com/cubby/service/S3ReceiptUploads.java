package com.cubby.service;

import com.cubby.domain.ReceiptImageKeys;
import com.cubby.domain.ReceiptValidation;
import com.cubby.dto.UploadUrlRequest;
import com.cubby.dto.UploadUrlResponse;
import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/** Presigns locally through an injected SDK presigner; never sends a storage request. */
public final class S3ReceiptUploads implements ReceiptUploads {
    public static final Duration URL_LIFETIME = Duration.ofMinutes(5);
    private final S3Presigner presigner;
    private final String bucketName;
    private final Supplier<UUID> uploadIds;

    public S3ReceiptUploads(S3Presigner presigner, String bucketName) {
        this(presigner, bucketName, UUID::randomUUID);
    }

    public S3ReceiptUploads(S3Presigner presigner, String bucketName, Supplier<UUID> uploadIds) {
        this.presigner = Objects.requireNonNull(presigner);
        ReceiptValidation.text(bucketName, "bucketName");
        this.bucketName = bucketName;
        this.uploadIds = Objects.requireNonNull(uploadIds);
    }

    @Override
    public UploadUrlResponse createUploadUrl(String userId, UploadUrlRequest request) {
        ReceiptImageKeys.requireOwner(userId);
        ReceiptValidation.required(request, "request");
        String key = ReceiptImageKeys.original(userId, uploadIds.get());
        var object = PutObjectRequest.builder()
                .bucket(bucketName).key(key)
                .contentType(request.contentType()).contentLength(request.contentLength())
                .ifNoneMatch("*")
                .build();
        var signed = presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(URL_LIFETIME).putObjectRequest(object).build());
        return new UploadUrlResponse(URI.create(signed.url().toString()), key,
                signed.expiration(), "PUT", signed.signedHeaders());
    }
}
