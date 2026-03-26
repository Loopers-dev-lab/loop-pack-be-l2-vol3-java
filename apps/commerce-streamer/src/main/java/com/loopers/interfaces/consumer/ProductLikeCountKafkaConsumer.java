package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.contract.kafka.LikeCountChangedMessage;
import com.loopers.application.product.ProductLikeCountConsumerService;
import com.loopers.confg.kafka.KafkaConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductLikeCountKafkaConsumer {

    private static final String CONSUMER_GROUP = "commerce-streamer-product-like-count";

    private final ProductLikeCountConsumerService productLikeCountConsumerService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = {"${loopers.kafka.topic.product-like-count:commerce.product.like-count.v1}"},
            containerFactory = KafkaConfig.BATCH_LISTENER
    )
    public void likeCountListener(List<ConsumerRecord<Object, Object>> messages, Acknowledgment acknowledgment) {
        for (ConsumerRecord<Object, Object> message : messages) {
            try {
                productLikeCountConsumerService.consume(CONSUMER_GROUP, read(message.value()));
            } catch (Exception e) {
                log.warn("product_like_count_consume_failed topic={} partition={} offset={}",
                        message.topic(), message.partition(), message.offset(), e);
                throw new IllegalStateException("Product like count consume failed", e);
            }
        }
        acknowledgment.acknowledge();
    }

    private LikeCountChangedMessage read(Object rawValue) throws Exception {
        if (rawValue instanceof byte[] bytes) {
            return objectMapper.readValue(bytes, LikeCountChangedMessage.class);
        }
        if (rawValue instanceof String value) {
            return objectMapper.readValue(value, LikeCountChangedMessage.class);
        }
        return objectMapper.convertValue(rawValue, LikeCountChangedMessage.class);
    }
}
