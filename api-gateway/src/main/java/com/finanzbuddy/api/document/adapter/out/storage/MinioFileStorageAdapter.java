package com.finanzbuddy.api.document.adapter.out.storage;

import com.finanzbuddy.api.document.DocumentStorage;
import com.finanzbuddy.api.document.domain.exception.FileStorageException;
import com.finanzbuddy.api.document.port.out.FileStoragePort;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import java.io.ByteArrayInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MinioFileStorageAdapter implements FileStoragePort {

    private static final Logger log = LoggerFactory.getLogger(MinioFileStorageAdapter.class);

    private final MinioClient minioClient;
    private final String bucket;

    public MinioFileStorageAdapter(
            MinioClient minioClient, @Value("${app.minio.bucket}") String bucket) {
        this.minioClient = minioClient;
        this.bucket = bucket;
    }

    @Override
    public String store(
            byte[] content, String contentType, long ownerId, long documentId, String filename) {
        String storagePath = DocumentStorage.objectKey(ownerId, documentId, filename);
        try {
            minioClient.putObject(
                    PutObjectArgs.builder().bucket(bucket).object(storagePath).stream(
                                    new ByteArrayInputStream(content), (long) content.length, -1L)
                            .contentType(
                                    contentType != null ? contentType : "application/octet-stream")
                            .build());
            return storagePath;
        } catch (Exception e) {
            throw new FileStorageException("Failed to store object " + storagePath, e);
        }
    }

    @Override
    public byte[] read(String storagePath) {
        try (GetObjectResponse object =
                minioClient.getObject(
                        GetObjectArgs.builder().bucket(bucket).object(storagePath).build())) {
            return object.readAllBytes();
        } catch (Exception e) {
            throw new FileStorageException("Failed to read object " + storagePath, e);
        }
    }

    @Override
    public void remove(String storagePath) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder().bucket(bucket).object(storagePath).build());
        } catch (Exception e) {
            log.error("Failed to remove object {}", storagePath, e);
        }
    }
}
