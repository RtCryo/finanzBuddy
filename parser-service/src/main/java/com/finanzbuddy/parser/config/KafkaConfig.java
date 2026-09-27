package com.finanzbuddy.parser.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finanzbuddy.parser.client.GatewayStatusClient;
import com.finanzbuddy.parser.document.DocumentStatus;
import com.finanzbuddy.parser.document.ErrorMessageTruncator;
import com.finanzbuddy.parser.event.DocumentUploadedEvent;
import com.finanzbuddy.parser.event.DocumentUploadedEventDeserializer;
import com.finanzbuddy.parser.service.TransactionPersistenceService;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfig {

    private static final Logger logger = LoggerFactory.getLogger(KafkaConfig.class);
    private static final long RETRY_INTERVAL_MS = 1000L;
    private static final long RETRY_MAX_ATTEMPTS = 3L;

    @Bean
    public DocumentUploadedEventDeserializer documentUploadedEventDeserializer(
            ObjectMapper objectMapper) {
        return new DocumentUploadedEventDeserializer(objectMapper);
    }

    @Bean
    public ConsumerFactory<String, DocumentUploadedEvent> consumerFactory(
            Environment env, DocumentUploadedEventDeserializer valueDeserializer) {
        Map<String, Object> props = new HashMap<>();
        props.put(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                env.getProperty("spring.kafka.bootstrap-servers", "localhost:29092"));
        props.put(
                ConsumerConfig.GROUP_ID_CONFIG,
                env.getProperty("spring.kafka.consumer.group-id", "parser-service"));
        props.put(
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                env.getProperty("spring.kafka.consumer.auto-offset-reset", "earliest"));
        ErrorHandlingDeserializer<DocumentUploadedEvent> errorHandlingDeserializer =
                new ErrorHandlingDeserializer<>(valueDeserializer);
        return new DefaultKafkaConsumerFactory<>(
                props, new StringDeserializer(), errorHandlingDeserializer);
    }

    @Bean
    @ConditionalOnMissingBean
    public ProducerFactory<String, Object> producerFactory(Environment env) {
        Map<String, Object> props = new HashMap<>();
        props.put(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                env.getProperty("spring.kafka.bootstrap-servers", "localhost:29092"));
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    @ConditionalOnMissingBean(KafkaTemplate.class)
    public KafkaTemplate<String, Object> kafkaTemplate(
            ProducerFactory<String, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    public DeadLetterPublishingRecoverer deadLetterPublishingRecoverer(
            KafkaTemplate<String, Object> kafkaTemplate,
            GatewayStatusClient gatewayStatusClient,
            TransactionPersistenceService transactionPersistenceService) {
        return new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) -> {
                    String originalTopic = record.topic();
                    String dltTopic = originalTopic + ".dlt";
                    logger.warn(
                            "Sending failed record to DLT: originalTopic={}, dltTopic={}",
                            originalTopic,
                            dltTopic,
                            exception);
                    if (record.value() instanceof DocumentUploadedEvent event) {
                        try {
                            if (event.documentId() != null) {
                                boolean alreadyProcessed =
                                        transactionPersistenceService.isProcessed(
                                                event.documentId());
                                gatewayStatusClient.reportStatus(
                                        event.documentId(),
                                        alreadyProcessed
                                                ? DocumentStatus.PROCESSED
                                                : DocumentStatus.FAILED,
                                        alreadyProcessed
                                                ? null
                                                : ErrorMessageTruncator.truncate(exception, 4000));
                            }
                        } catch (Exception statusException) {
                            logger.error(
                                    "Failed to report terminal failure for documentId={}",
                                    event.documentId(),
                                    statusException);
                        }
                    }
                    return new org.apache.kafka.common.TopicPartition(dltTopic, record.partition());
                });
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, DocumentUploadedEvent>
            kafkaListenerContainerFactory(
                    ConsumerFactory<String, DocumentUploadedEvent> consumerFactory,
                    DeadLetterPublishingRecoverer deadLetterPublishingRecoverer) {
        ConcurrentKafkaListenerContainerFactory<String, DocumentUploadedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        DefaultErrorHandler errorHandler =
                new DefaultErrorHandler(
                        deadLetterPublishingRecoverer,
                        new FixedBackOff(RETRY_INTERVAL_MS, RETRY_MAX_ATTEMPTS));
        errorHandler.addNotRetryableExceptions(
                IllegalArgumentException.class, UnsupportedOperationException.class);
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }
}
