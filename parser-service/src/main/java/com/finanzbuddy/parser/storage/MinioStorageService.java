package com.finanzbuddy.parser.storage;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import java.io.InputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MinioStorageService {

    private static final Logger logger = LoggerFactory.getLogger(MinioStorageService.class);

    private final MinioClient minioClient;
    private final String bucket;

    public MinioStorageService(
            MinioClient minioClient, @Value("${parser.storage.minio.bucket}") String bucket) {
        this.minioClient = minioClient;
        this.bucket = bucket;
    }

    public InputStream download(String storagePath) {
        logger.info("Downloading file from MinIO: bucket={}, path={}", bucket, storagePath);
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder().bucket(bucket).object(storagePath).build());
        } catch (Exception e) {
            logger.error(
                    "Failed to download file from MinIO: bucket={}, path={}",
                    bucket,
                    storagePath,
                    e);
            throw new StorageException("Failed to download file: " + storagePath, e);
        }
    }
}
