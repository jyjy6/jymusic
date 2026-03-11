package jymusic.jym_catalog_service.service;

import jymusic.jym_catalog_service.common.GlobalErrorHandler.GlobalException;
import jymusic.jym_catalog_service.dto.request.PresignedUrlRequest;
import jymusic.jym_catalog_service.dto.response.PresignedUrlResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaService {

    private final S3Presigner s3Presigner;

    @Value("${spring.cloud.aws.s3.bucket}")
    private String bucketName;

    @Value("${spring.cloud.aws.s3.presigned-url-expiry-minutes:10}")
    private long expiryMinutes;

    public PresignedUrlResponse generatePresignedUrl(PresignedUrlRequest request) {
        String objectKey = "products/" + UUID.randomUUID() + "-" + request.getFilename();

        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .contentType(request.getContentType())
                    .build();

            PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(
                    PutObjectPresignRequest.builder()
                            .signatureDuration(Duration.ofMinutes(expiryMinutes))
                            .putObjectRequest(putObjectRequest)
                            .build()
            );

            return PresignedUrlResponse.builder()
                    .presignedUrl(presignedRequest.url().toString())
                    .objectKey(objectKey)
                    .build();

        } catch (Exception e) {
            log.error("Presigned URL 생성 실패: {}", e.getMessage(), e);
            throw new GlobalException(
                    "Presigned URL 생성에 실패했습니다.", "ERR_PRESIGNED_URL_FAILED", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
