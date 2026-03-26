package com.loopers.interfaces.consumer;

import com.loopers.confg.kafka.KafkaConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 주문 이벤트 Kafka Consumer.
 *
 * <p>order-events 토픽에서 ORDER_CREATED, ORDER_CANCELLED, ORDER_EXPIRED
 * 이벤트를 배치로 수신하여 {@link OrderEventProcessor}에 위임한다.</p>
 *
 * <p>Outbox 패턴으로 발행된 이벤트이므로 멱등 처리(event_handled)를 적용한다.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventConsumer {

    private final OrderEventProcessor orderEventProcessor;

    @KafkaListener(
        topics = "order-events",
        containerFactory = KafkaConfig.BATCH_LISTENER,
        groupId = "streamer-order"
    )
    public void consume(List<ConsumerRecord<Object, Object>> records, Acknowledgment ack) {
        log.debug("[OrderConsumer] 수신 {}건", records.size());

        for (ConsumerRecord<Object, Object> record : records) {
            try {
                orderEventProcessor.process(record);
            } catch (Exception e) {
                log.error("[OrderConsumer] 처리 실패 — offset={}, key={}",
                    record.offset(), record.key(), e);
            }
        }

        ack.acknowledge();
    }
}
