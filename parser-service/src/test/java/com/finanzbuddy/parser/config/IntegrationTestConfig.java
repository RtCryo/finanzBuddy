package com.finanzbuddy.parser.config;

import com.finanzbuddy.parser.processing.domain.exception.FileFetchException;
import com.finanzbuddy.parser.processing.port.out.FileFetchPort;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@TestConfiguration
public class IntegrationTestConfig {

    public static final String SAMPLE_CSV =
            "BookingDate;ValueDate;Amount;Currency;Counterparty;Description;Category\n"
                    + "10.01.24;10.01.24;-25.99;EUR;Coffee Shop;Morning coffee;Food\n"
                    + "11.01.24;11.01.24;2500.00;EUR;Employer;Monthly salary;Income";

    private static MockRestServiceServer mockServer;

    @Bean
    @Primary
    public RestClient.Builder testRestClientBuilder() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        return builder;
    }

    public static MockRestServiceServer getMockServer() {
        return mockServer;
    }

    @Bean
    @Primary
    public FileFetchPort testFileFetchPort() {
        return storagePath -> {
            if (storagePath == null || !storagePath.startsWith("csv/")) {
                throw new FileFetchException(storagePath, null);
            }
            return new ByteArrayInputStream(SAMPLE_CSV.getBytes(StandardCharsets.UTF_8));
        };
    }

    @Bean
    @Primary
    public KafkaTemplate<String, Object> kafkaTemplate() {
        String brokers = System.getProperty("spring.embedded.kafka.brokers", "localhost:9092");
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, brokers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
    }
}
