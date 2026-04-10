package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.confg.kafka.KafkaConfig;
import com.loopers.domain.metrics.ProductMetricsService;
import com.loopers.domain.ranking.RankingFlushBatchService;
import com.loopers.support.kafka.KafkaOutboxMessage;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@Component
public class ProductMetricsConsumer {

    private final ProductMetricsService productMetricsService;
    private final RankingFlushBatchService rankingFlushBatchService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = {"product.like.events", "product.payment.events", "product.view.events"},
        groupId = "product-metrics-consumer",
        containerFactory = KafkaConfig.BATCH_LISTENER
    )
    public void consume(List<ConsumerRecord<Object, Object>> messages, Acknowledgment acknowledgment) {
        List<String> processedEventIds = new ArrayList<>();

        for (ConsumerRecord<Object, Object> message : messages) {
            try {
                byte[] valueBytes = (byte[]) message.value();
                KafkaOutboxMessage kafkaMessage = objectMapper.readValue(valueBytes, KafkaOutboxMessage.class);
                productMetricsService.handle(kafkaMessage);
                processedEventIds.add(kafkaMessage.eventId());
            } catch (IOException e) {
                throw new RuntimeException("Kafka 메시지 역직렬화 실패. topic=" + message.topic() + ", offset=" + message.offset(), e);
            }
        }

        rankingFlushBatchService.flushProcessedEvents(processedEventIds);
        acknowledgment.acknowledge();
    }
}
