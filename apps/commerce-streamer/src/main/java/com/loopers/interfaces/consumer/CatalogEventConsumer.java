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
 * 카탈로그 이벤트 Kafka Consumer.
 *
 * <p>catalog-events 토픽에서 PRODUCT_VIEWED, PRODUCT_LIKED, PRODUCT_UNLIKED
 * 이벤트를 배치로 수신하여 {@link CatalogEventProcessor}에 위임한다.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CatalogEventConsumer {

    private final CatalogEventProcessor catalogEventProcessor;

    @KafkaListener(
        topics = "catalog-events",
        containerFactory = KafkaConfig.BATCH_LISTENER,
        groupId = "streamer-catalog"
    )
    public void consume(List<ConsumerRecord<Object, Object>> records, Acknowledgment ack) {
        log.debug("[CatalogConsumer] 수신 {}건", records.size());

        for (ConsumerRecord<Object, Object> record : records) {
            try {
                catalogEventProcessor.process(record);
            } catch (Exception e) {
                log.error("[CatalogConsumer] 처리 실패 — offset={}, key={}",
                    record.offset(), record.key(), e);
            }
        }

        ack.acknowledge();
    }
}
