package com.builddash.backend.infra.storage;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class S3ObjectStorageAdapterIT {

    private static final String BUCKET = "test-invoices";

    @Container
    private static final MinIOContainer MINIO = new MinIOContainer("minio/minio:RELEASE.2024-01-16T16-07-38Z");

    private static S3Client s3Client;
    private static S3Presigner s3Presigner;
    private static S3ObjectStorageAdapter adapter;

    @BeforeAll
    static void setUp() {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(MINIO.getUserName(), MINIO.getPassword());
        StaticCredentialsProvider credentialsProvider = StaticCredentialsProvider.create(credentials);
        Region region = Region.AP_SOUTH_1;
        URI endpoint = URI.create(MINIO.getS3URL());

        S3Configuration s3Config = S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .build();

        s3Client = S3Client.builder()
                .region(region)
                .credentialsProvider(credentialsProvider)
                .endpointOverride(endpoint)
                .serviceConfiguration(s3Config)
                .build();

        s3Presigner = S3Presigner.builder()
                .region(region)
                .credentialsProvider(credentialsProvider)
                .endpointOverride(endpoint)
                .serviceConfiguration(s3Config)
                .build();

        s3Client.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());

        adapter = new S3ObjectStorageAdapter(s3Client, s3Presigner, BUCKET);
    }

    @AfterAll
    static void tearDown() {
        if (s3Client != null) {
            s3Client.close();
        }
        if (s3Presigner != null) {
            s3Presigner.close();
        }
    }

    @Test
    void store_and_signedUrl_fetchesStoredBytesByteForByte() throws Exception {
        String key = "invoices/order-123/INV-2627-000001.pdf";
        byte[] expectedContent = "%PDF-1.4 test invoice content with UTF-8 data: ₹ 1,500.00".getBytes(StandardCharsets.UTF_8);

        String storedKey = adapter.store(key, expectedContent, "application/pdf");
        assertThat(storedKey).isEqualTo(key);

        String signedUrl = adapter.signedUrl(key, Duration.ofMinutes(10));
        assertThat(signedUrl).isNotBlank();
        assertThat(signedUrl).startsWith(MINIO.getS3URL());

        HttpClient httpClient = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(signedUrl))
                .GET()
                .build();

        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo(expectedContent);
    }
}
