package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.outbox.OutboxAckMessage;
import com.loopers.domain.outbox.OutboxEventRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class OutboxAckKafkaConsumerTest {

    @Test
    @DisplayName("ack 메시지를 수신하면 outbox 상태를 ACKED로 변경한다")
    void ackListener_marksOutboxAcked() throws Exception {
        OutboxEventRepository outboxEventRepository = mock(OutboxEventRepository.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        OutboxAckKafkaConsumer consumer = new OutboxAckKafkaConsumer(outboxEventRepository, objectMapper);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);

        UUID eventId = UUID.randomUUID();
        OutboxAckMessage ackMessage = new OutboxAckMessage(eventId, "collector", Instant.now());
        ConsumerRecord<Object, Object> record = new ConsumerRecord<>("commerce.outbox.ack.v1", 0, 0L, eventId.toString(), objectMapper.writeValueAsString(ackMessage));

        consumer.ackListener(List.of(record), acknowledgment);

        verify(outboxEventRepository, times(1)).markAckedByEventId(eq(eventId), org.mockito.ArgumentMatchers.any());
        verify(acknowledgment, times(1)).acknowledge();
    }
}
