package com.builddash.backend.infra.storage;

import com.builddash.backend.domain.port.ObjectStorage;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.net.URI;
import java.time.Duration;

@Slf4j
@Component
public class S3ObjectStorageAdapter implements ObjectStorage {

    @Value("${storage.s3.bucket:builddash-invoices}")
    private String bucket;

    @Value("${storage.s3.region:ap-south-1}")
    private String region;

    @Value("${storage.s3.endpoint:}")
    private String endpoint;

    @Value("${storage.s3.access-key:minioadmin}")
    private String accessKey;

    @Value("${storage.s3.secret-key:minioadminpassword}")
    private String secretKey;

    @Value("${storage.s3.path-style-access:true}")
    private boolean pathStyleAccess;

    private S3Client s3Client;
    private S3Presigner s3Presigner;

    public S3ObjectStorageAdapter() {
    }

    public S3ObjectStorageAdapter(S3Client s3Client, S3Presigner s3Presigner, String bucket) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.bucket = bucket;
    }

    @PostConstruct
    public void init() {
        if (s3Client != null && s3Presigner != null) {
            return;
        }

        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);
        StaticCredentialsProvider credentialsProvider = StaticCredentialsProvider.create(credentials);
        Region awsRegion = Region.of(region);

        S3Configuration s3Configuration = S3Configuration.builder()
                .pathStyleAccessEnabled(pathStyleAccess)
                .build();

        S3ClientBuilder clientBuilder = S3Client.builder()
                .region(awsRegion)
                .credentialsProvider(credentialsProvider)
                .serviceConfiguration(s3Configuration);

        S3Presigner.Builder presignerBuilder = S3Presigner.builder()
                .region(awsRegion)
                .credentialsProvider(credentialsProvider)
                .serviceConfiguration(s3Configuration);

        if (endpoint != null && !endpoint.isBlank()) {
            URI endpointUri = URI.create(endpoint);
            clientBuilder.endpointOverride(endpointUri);
            presignerBuilder.endpointOverride(endpointUri);
        }

        this.s3Client = clientBuilder.build();
        this.s3Presigner = presignerBuilder.build();

        ensureBucketExists();
    }

    private void ensureBucketExists() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (NoSuchBucketException e) {
            log.info("Bucket {} does not exist, creating it", bucket);
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        } catch (Exception e) {
            log.warn("Could not verify or create bucket {}: {}", bucket, e.getMessage());
        }
    }

    @Override
    public String store(String key, byte[] bytes, String contentType) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType != null ? contentType : "application/octet-stream")
                .build();

        s3Client.putObject(putObjectRequest, RequestBody.fromBytes(bytes));
        return key;
    }

    @Override
    public String signedUrl(String key, Duration ttl) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(ttl != null ? ttl : Duration.ofMinutes(15))
                .getObjectRequest(getObjectRequest)
                .build();

        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }
}
