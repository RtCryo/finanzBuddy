package com.finanzbuddy.parser.processing.adapter.out.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.finanzbuddy.parser.processing.domain.exception.FileFetchException;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class MinioFileFetchAdapterTest {

    private final MinioClient minioClient = mock(MinioClient.class);
    private final MinioFileFetchAdapter adapter =
            new MinioFileFetchAdapter(minioClient, "test-bucket");

    @Test
    void downloadsFileFromMinio() throws Exception {
        InputStream content =
                new ByteArrayInputStream("csv-content".getBytes(StandardCharsets.UTF_8));
        GetObjectResponse response =
                new GetObjectResponse(null, "test-bucket", "test-region", "csv/1", content);
        when(minioClient.getObject(any())).thenReturn(response);

        InputStream result = adapter.fetch("csv/1");

        assertThat(result).isNotNull();
        assertThat(new String(result.readAllBytes(), StandardCharsets.UTF_8))
                .isEqualTo("csv-content");
    }

    @Test
    void wrapsMinioExceptionsInFileFetchException() throws Exception {
        when(minioClient.getObject(any())).thenThrow(new RuntimeException("minio error"));

        assertThatThrownBy(() -> adapter.fetch("csv/1"))
                .isInstanceOf(FileFetchException.class)
                .hasMessageContaining("Failed to fetch file: csv/1");
    }
}
