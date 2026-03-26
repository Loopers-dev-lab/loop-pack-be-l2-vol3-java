package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.outbox.OutboxAckMessage;
import com.loopers.domain.outbox.OutboxEventRepository;
import com.loopers.confg.kafka.KafkaConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxAckKafkaConsumer {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = {"${loopers.kafka.topic.outbox-ack:commerce.outbox.ack.v1}"},
            containerFactory = KafkaConfig.BATCH_LISTENER,
            groupId = "commerce-api-outbox-ack"
    )
    public void ackListener(List<ConsumerRecord<Object, Object>> messages, Acknowledgment acknowledgment) {
        for (ConsumerRecord<Object, Object> message : messages) {
            try {
                OutboxAckMessage ackMessage = toAckMessage(message.value());
                outboxEventRepository.markAckedByEventId(
                        ackMessage.eventId(),
                        ZonedDateTime.ofInstant(ackMessage.ackedAt(), ZonedDateTime.now().getZone())
                );
            } catch (Exception e) {
                log.warn("outbox_ack_consume_failed topic={} partition={} offset={}",
                        message.topic(), message.partition(), message.offset(), e);
                throw new IllegalStateException("Outbox ack consume failed", e);
            }
        }
        acknowledgment.acknowledge();
    }

    private OutboxAckMessage toAckMessage(Object rawValue) throws Exception {
        if (rawValue instanceof byte[] bytes) {
            return objectMapper.readValue(bytes, OutboxAckMessage.class);
        }
        if (rawValue instanceof String value) {
            return objectMapper.readValue(value, OutboxAckMessage.class);
        }
        return objectMapper.convertValue(rawValue, OutboxAckMessage.class);
    }
}
