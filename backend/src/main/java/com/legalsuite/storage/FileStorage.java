package com.legalsuite.storage;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Where uploaded documents, logos, firm templates and generated files live.
 *
 * <p>Default is local disk under {@code legalsuite.upload-dir}; the stored reference is the absolute
 * file path (unchanged from earlier releases). With {@code legalsuite.storage.type=s3} new files go
 * to an S3-compatible bucket (AWS S3, Supabase Storage, MinIO, R2) and the reference is
 * {@code s3://bucket/key}. Reads dispatch on the reference, so old local files keep working.
 */
@Component
public class FileStorage {

    public static final String S3_PREFIX = "s3://";

    private final Path root;
    private final String type;
    private final String bucket;
    private final S3Client s3;

    public FileStorage(@Value("${legalsuite.upload-dir:./uploads}") String uploadDir,
                       @Value("${legalsuite.storage.type:local}") String type,
                       @Value("${legalsuite.storage.s3.endpoint:}") String endpoint,
                       @Value("${legalsuite.storage.s3.region:us-east-1}") String region,
                       @Value("${legalsuite.storage.s3.bucket:}") String bucket,
                       @Value("${legalsuite.storage.s3.access-key:}") String accessKey,
                       @Value("${legalsuite.storage.s3.secret-key:}") String secretKey) {
        this.root = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.type = type == null || type.isBlank() ? "local" : type.trim().toLowerCase();
        this.bucket = bucket;
        if ("s3".equals(this.type)) {
            if (bucket == null || bucket.isBlank() || accessKey == null || accessKey.isBlank()
                    || secretKey == null || secretKey.isBlank()) {
                throw new IllegalStateException("S3 storage needs LEGALSUITE_STORAGE_S3_BUCKET, _ACCESS_KEY and _SECRET_KEY.");
            }
            var b = S3Client.builder()
                    .httpClient(UrlConnectionHttpClient.create())
                    .region(Region.of(region == null || region.isBlank() ? "us-east-1" : region))
                    .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                    .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).chunkedEncodingEnabled(false).build())
                    .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                    .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED);
            if (endpoint != null && !endpoint.isBlank()) b.endpointOverride(URI.create(endpoint.trim()));
            this.s3 = b.build();
        } else {
            this.s3 = null;
        }
    }

    public String type() { return type; }

    /** Stores bytes under a relative key such as {@code <tenant>/<uuid>-name.pdf}; returns the reference to save. */
    public String put(String key, byte[] bytes, String contentType) throws IOException {
        String clean = key.replace('\\', '/');
        if (clean.startsWith("/") || clean.contains("..")) throw new IOException("Invalid storage key");
        if (s3 != null) {
            s3.putObject(PutObjectRequest.builder().bucket(bucket).key(clean)
                            .contentType(contentType == null ? "application/octet-stream" : contentType).build(),
                    RequestBody.fromBytes(bytes));
            return S3_PREFIX + bucket + "/" + clean;
        }
        Path dest = root.resolve(clean).normalize();
        if (!dest.startsWith(root)) throw new IOException("Invalid storage key");
        Files.createDirectories(dest.getParent());
        Files.write(dest, bytes);
        return dest.toString();
    }

    public byte[] read(String ref) throws IOException {
        if (ref == null) throw new IOException("No file");
        if (ref.startsWith(S3_PREFIX)) {
            Loc l = loc(ref);
            try {
                return s3().getObjectAsBytes(GetObjectRequest.builder().bucket(l.bucket).key(l.key).build()).asByteArray();
            } catch (NoSuchKeyException e) {
                throw new IOException("File not found", e);
            } catch (RuntimeException e) {
                throw new IOException("Storage read failed", e);
            }
        }
        return Files.readAllBytes(Path.of(ref));
    }

    public void delete(String ref) {
        if (ref == null) return;
        try {
            if (ref.startsWith(S3_PREFIX)) {
                Loc l = loc(ref);
                s3().deleteObject(DeleteObjectRequest.builder().bucket(l.bucket).key(l.key).build());
            } else if (!ref.startsWith("seed://")) {
                Files.deleteIfExists(Path.of(ref));
            }
        } catch (Exception ignored) {
            // Best effort: an orphaned object is harmless.
        }
    }

    private S3Client s3() throws IOException {
        if (s3 == null) throw new IOException("This file is in S3 storage but S3 is not configured.");
        return s3;
    }

    private record Loc(String bucket, String key) {}

    private static Loc loc(String ref) {
        String rest = ref.substring(S3_PREFIX.length());
        int i = rest.indexOf('/');
        return new Loc(rest.substring(0, i), rest.substring(i + 1));
    }

    @PreDestroy
    void close() { if (s3 != null) s3.close(); }
}
