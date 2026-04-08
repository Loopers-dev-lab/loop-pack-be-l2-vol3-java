package com.loopers.infrastructure.outbox;

import com.loopers.support.outbox.OutboxEvent;
import com.loopers.support.outbox.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OutboxEventService.saveAndPublish가 Kafka에 envelope { eventId, eventType, payload } Map을
 * 객체로 send 하는지 검증한다 (이중 인코딩 방지).
 */
class OutboxEnvelopeTest {

    @SuppressWarnings("unchecked")
    private KafkaTemplate<Object, Object> kafkaTemplate = mock(KafkaTemplate.class);
    private OutboxEventRepository repository = mock(OutboxEventRepository.class);
    private OutboxEventFactory factory = mock(OutboxEventFactory.class);

    private OutboxEventService service;

    @BeforeEach
    void setUp() {
        when(kafkaTemplate.send(any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        service = new OutboxEventService(repository, factory, kafkaTemplate);
        // ManualTx 시뮬레이션: afterCommit 콜백이 즉시 실행되도록 동기화 활성화
        TransactionSynchronizationManager.initSynchronization();
    }

    @Test
    @DisplayName("saveAndPublish는 envelope Map을 그대로 KafkaTemplate에 넘긴다")
    void sendsEnvelopeMap() {
        OutboxEvent stub = OutboxEvent.create(
                "evt-uuid-1",
                "payment.completed",
                "Order",
                "42",
                "{\"paymentId\":1,\"orderId\":42,\"userId\":7,\"amount\":1500}",
                "order-events"
        );
        when(factory.create(any(), any(), any(), any(), any())).thenReturn(stub);

        service.saveAndPublish("payment.completed", "Order", "42", "order-events", new Object());

        // afterCommit 콜백 트리거
        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCommit();
        }

        ArgumentCaptor<Object> valueCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq("order-events"), eq("42"), valueCaptor.capture());

        Object value = valueCaptor.getValue();
        assertThat(value).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = (Map<String, Object>) value;
        assertThat(envelope.get("eventId")).isEqualTo("evt-uuid-1");
        assertThat(envelope.get("eventType")).isEqualTo("payment.completed");
        assertThat(envelope.get("payload"))
                .isEqualTo("{\"paymentId\":1,\"orderId\":42,\"userId\":7,\"amount\":1500}");
    }
}
