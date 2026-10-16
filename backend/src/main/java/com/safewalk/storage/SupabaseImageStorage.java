package com.safewalk.storage;

import java.net.URI;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * Supabase Storage 구현 (S3 호환 API + AWS SDK for Java v2).
 *
 * .env에 STORAGE_TYPE=supabase 와 STORAGE_ENDPOINT/REGION/ACCESS_KEY/SECRET_KEY/BUCKET을 넣으면 사용된다.
 * 나중에 AWS S3로 옮길 때는 엔드포인트와 키만 바꾸면 된다(코드 수정 불필요).
 *
 * 주의: S3 Access Key는 RLS를 무시하고 모든 버킷에 접근할 수 있는 서버 전용 키다. Android 코드에 넣지 말 것.
 */
@Component
@ConditionalOnProperty(name = "storage.type", havingValue = "supabase")
public class SupabaseImageStorage implements ImageStorage, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(SupabaseImageStorage.class);

    private final String bucket;
    private final S3Client client;
    private final S3Presigner presigner;

    public SupabaseImageStorage(
            @Value("${storage.endpoint}") String endpoint,
            @Value("${storage.region}") String region,
            @Value("${storage.access-key}") String accessKey,
            @Value("${storage.secret-key}") String secretKey,
            @Value("${storage.bucket}") String bucket
    ) {
        requireConfigured("STORAGE_ENDPOINT", endpoint);
        requireConfigured("STORAGE_REGION", region);
        requireConfigured("STORAGE_ACCESS_KEY", accessKey);
        requireConfigured("STORAGE_SECRET_KEY", secretKey);
        requireConfigured("STORAGE_BUCKET", bucket);

        this.bucket = bucket.trim();

        URI endpointUri = URI.create(endpoint.trim());
        Region awsRegion = Region.of(region.trim());
        StaticCredentialsProvider credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey.trim(), secretKey.trim()));
        // Supabase는 "엔드포인트/버킷/키" 형태의 경로 방식 주소만 지원한다.
        S3Configuration s3Config = S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .build();

        this.client = S3Client.builder()
                .endpointOverride(endpointUri)
                .region(awsRegion)
                .credentialsProvider(credentials)
                .serviceConfiguration(s3Config)
                // AWS SDK 2.30부터 기본으로 붙이는 CRC 체크섬 헤더를 S3 호환 서비스가 지원하지 않는 경우가 있어
                // 꼭 필요한 요청에만 계산하도록 끈다.
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .build();

        this.presigner = S3Presigner.builder()
                .endpointOverride(endpointUri)
                .region(awsRegion)
                .credentialsProvider(credentials)
                .serviceConfiguration(s3Config)
                .build();

        log.info("이미지 저장소: Supabase Storage (bucket={}, endpoint={})", this.bucket, endpointUri);
    }

    @Override
    public void upload(String key, byte[] bytes, String contentType) {
        try {
            client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(contentType)
                            .build(),
                    RequestBody.fromBytes(bytes));
        } catch (SdkException e) {
            throw new ImageStorageException("이미지를 저장소에 올리지 못했습니다. 잠시 후 다시 시도해 주세요.", e);
        }
    }

    @Override
    public String createViewUrl(String key, Duration ttl) {
        try {
            GetObjectPresignRequest request = GetObjectPresignRequest.builder()
                    .signatureDuration(ttl)
                    .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(key).build())
                    .build();
            return presigner.presignGetObject(request).url().toString();
        } catch (SdkException e) {
            throw new ImageStorageException("이미지 주소를 만들지 못했습니다.", e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (SdkException e) {
            throw new ImageStorageException("저장소에서 이미지를 삭제하지 못했습니다.", e);
        }
    }

    @Override
    public void destroy() {
        presigner.close();
        client.close();
    }

    private static void requireConfigured(String envName, String value) {
        if (value == null || value.isBlank() || value.contains("<")) {
            throw new IllegalStateException(
                    "STORAGE_TYPE=supabase 인데 " + envName + " 값이 비어 있습니다. backend/.env를 확인하세요.");
        }
    }
}
