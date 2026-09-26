package com.finanzbuddy.api.document.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.finanzbuddy.api.entity.OutboxEvent;
import com.finanzbuddy.api.repository.OutboxEventRepository;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private KafkaTemplate<Object, Object> kafkaTemplate;
    @Mock private PlatformTransactionManager txManager;
    @Mock private OutboxMetrics outboxMetrics;

    private OutboxRelay relay(int maxAttempts) {
        return new OutboxRelay(
                outboxEventRepository,
                kafkaTemplate,
                txManager,
                outboxMetrics,
                100,
                1000L,
                maxAttempts,
                5000L,
                300000L);
    }

    private OutboxEvent event(long id, String topic, String payload) {
        OutboxEvent event = new OutboxEvent(id, topic, payload);
        ReflectionTestUtils.setField(event, "id", id);
        return event;
    }

    private void stubPending(OutboxEvent... events) {
        when(outboxEventRepository.findPublishable(any(), any())).thenReturn(List.of(events));
    }

    private void stubSendSucceeds() {
        CompletableFuture<SendResult<Object, Object>> ok = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(ok);
    }

    private void stubSendFails() {
        CompletableFuture<SendResult<Object, Object>> failed =
                CompletableFuture.failedFuture(new RuntimeException("broker down"));
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(failed);
    }

    @Test
    void sendsStoredPayloadVerbatimAndMarksPublished() {
        OutboxEvent event = event(1L, "documents.csv", "{\"documentId\":1}");
        stubPending(event);
        stubSendSucceeds();
        when(outboxEventRepository.findById(1L)).thenReturn(Optional.of(event));

        relay(10).publishPending();

        verify(kafkaTemplate).send("documents.csv", "1", "{\"documentId\":1}");
        assertThat(event.getPublishedAt()).isNotNull();
        verify(outboxMetrics, never()).recordDeadLettered();
    }

    @Test
    void deadLettersAndCountsAfterExhaustingAttempts() {
        OutboxEvent event = event(1L, "documents.csv", "{\"documentId\":1}");
        stubPending(event);
        stubSendFails();
        when(outboxEventRepository.findById(1L)).thenReturn(Optional.of(event));

        relay(1).publishPending();

        assertThat(event.getDeadLetteredAt()).isNotNull();
        verify(outboxMetrics).recordDeadLettered();
    }

    @Test
    void attemptsEveryEventEvenWhenEarlierOnesFail() {
        // A failing event at the head of the batch must not block newer, deliverable events.
        stubPending(
                event(1L, "documents.csv", "a"),
                event(2L, "documents.csv", "b"),
                event(3L, "documents.csv", "c"),
                event(4L, "documents.csv", "d"),
                event(5L, "documents.csv", "e"));
        stubSendFails();
        when(outboxEventRepository.findById(anyLong()))
                .thenAnswer(inv -> Optional.of(event(inv.getArgument(0), "documents.csv", "x")));

        relay(10).publishPending();

        verify(kafkaTemplate, times(5)).send(any(), any(), any());
    }
}
