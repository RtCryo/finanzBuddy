package com.finanzbuddy.parser.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class MinioStorageServiceTest {

    private final MinioClient minioClient = mock(MinioClient.class);
    private final MinioStorageService storageService =
            new MinioStorageService(minioClient, "test-bucket");

    @Test
    void downloadsFileFromMinio() throws Exception {
        InputStream content =
                new ByteArrayInputStream("csv-content".getBytes(StandardCharsets.UTF_8));
        GetObjectResponse response =
                new GetObjectResponse(null, "test-bucket", "test-region", "csv/1", content);
        when(minioClient.getObject(any())).thenReturn(response);

        InputStream result = storageService.download("csv/1");

        assertThat(result).isNotNull();
        assertThat(new String(result.readAllBytes(), StandardCharsets.UTF_8))
                .isEqualTo("csv-content");
    }

    @Test
    void wrapsMinioExceptionsInStorageException() throws Exception {
        when(minioClient.getObject(any())).thenThrow(new RuntimeException("minio error"));

        assertThatThrownBy(() -> storageService.download("csv/1"))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("Failed to download file: csv/1");
    }
}
