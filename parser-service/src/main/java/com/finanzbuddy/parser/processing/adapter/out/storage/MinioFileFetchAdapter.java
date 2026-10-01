package com.finanzbuddy.parser.processing.adapter.out.storage;

import com.finanzbuddy.parser.processing.domain.exception.FileFetchException;
import com.finanzbuddy.parser.processing.port.out.FileFetchPort;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import java.io.InputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MinioFileFetchAdapter implements FileFetchPort {

    private static final Logger logger = LoggerFactory.getLogger(MinioFileFetchAdapter.class);

    private final MinioClient minioClient;
    private final String bucket;

    public MinioFileFetchAdapter(
            MinioClient minioClient, @Value("${parser.storage.minio.bucket}") String bucket) {
        this.minioClient = minioClient;
        this.bucket = bucket;
    }

    @Override
    public InputStream fetch(String storagePath) {
        logger.debug("Downloading file from MinIO: bucket={}, path={}", bucket, storagePath);
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder().bucket(bucket).object(storagePath).build());
        } catch (Exception e) {
            logger.error(
                    "Failed to download file from MinIO: bucket={}, path={}",
                    bucket,
                    storagePath,
                    e);
            throw new FileFetchException(storagePath, e);
        }
    }
}
