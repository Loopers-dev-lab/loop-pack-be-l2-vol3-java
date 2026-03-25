package com.loopers.interfaces.consumer;

import java.util.List;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.loopers.application.metrics.ProductMetricsService;
import com.loopers.confg.kafka.KafkaConfig;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 상품 지표 집계를 위한 Kafka Consumer.
 *
 * <p>좋아요/주문 완료/상품 조회 이벤트를 소비하여 {@code product_metrics} 테이블의
 * 좋아요 수, 판매량, 조회 수를 델타 방식으로 업데이트한다.</p>
 *
 * <p>개별 메시지 처리 실패 시 로그를 남기고 skip한다.
 * 배치 내 나머지 메시지는 정상 처리되며, 배치 완료 후 manual ACK한다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductMetricsConsumer {

    private static final String TOPIC_LIKED = "like-liked-v1";
    private static final String TOPIC_UNLIKED = "like-unliked-v1";
    private static final String TOPIC_ORDER_COMPLETED = "order-completed-v1";
    private static final String TOPIC_PRODUCT_VIEWED = "product-viewed-v1";

    private final ProductMetricsService productMetricsService;

    /**
     * 좋아요 이벤트를 소비하여 상품별 좋아요 수를 갱신한다.
     *
     * <p>{@code like-liked-v1} 토픽이면 +1, {@code like-unliked-v1} 토픽이면 -1.</p>
     *
     * @param messages Kafka 메시지 배치 (payload: {@code {"eventId":"...", "productId":1}})
     * @param ack      manual ACK
     */
    @KafkaListener(
            topics = {TOPIC_LIKED, TOPIC_UNLIKED},
            containerFactory = KafkaConfig.BATCH_LISTENER
    )
    public void consumeLikeEvents(List<ConsumerRecord<String, JsonNode>> messages, Acknowledgment ack) {
        log.debug("[LikeMetrics] 배치 수신: size={}", messages.size());
        for (ConsumerRecord<String, JsonNode> record : messages) {
            try {
                Long productId = record.value().get("productId").asLong();

                if (TOPIC_LIKED.equals(record.topic())) {
                    productMetricsService.incrementLikeCount(productId);
                    log.debug("[LikeMetrics] 좋아요 증가: productId={}", productId);
                } else {
                    productMetricsService.decrementLikeCount(productId);
                    log.debug("[LikeMetrics] 좋아요 감소: productId={}", productId);
                }
            } catch (Exception e) {
                log.error("[LikeMetrics] 처리 실패: topic={}, offset={}", record.topic(), record.offset(), e);
            }
        }
        ack.acknowledge();
    }

    /**
     * 주문 완료 이벤트를 소비하여 상품별 판매량을 갱신한다.
     *
     * <p>이벤트 payload의 {@code orderItems}에서 상품별 수량을 추출하여
     * 각 상품의 {@code orderCount}에 누적한다.</p>
     *
     * @param messages Kafka 메시지 배치 (payload: {@code {"eventId":"...", "orderId":1, "orderItems":[...]}})
     * @param ack      manual ACK
     */
    @KafkaListener(
            topics = TOPIC_ORDER_COMPLETED,
            containerFactory = KafkaConfig.BATCH_LISTENER
    )
    public void consumeOrderEvents(List<ConsumerRecord<String, JsonNode>> messages, Acknowledgment ack) {
        log.debug("[OrderMetrics] 배치 수신: size={}", messages.size());
        for (ConsumerRecord<String, JsonNode> record : messages) {
            try {
                JsonNode orderItems = record.value().get("orderItems");
                for (JsonNode item : orderItems) {
                    Long productId = item.get("productId").asLong();
                    Long quantity = item.get("quantity").asLong();
                    productMetricsService.addOrderCount(productId, quantity);
                    log.debug("[OrderMetrics] 판매량 추가: productId={}, quantity={}", productId, quantity);
                }
            } catch (Exception e) {
                log.error("[OrderMetrics] 처리 실패: topic={}, offset={}", record.topic(), record.offset(), e);
            }
        }
        ack.acknowledge();
    }

    /**
     * 상품 조회 이벤트를 소비하여 상품별 조회 수를 갱신한다.
     *
     * @param messages Kafka 메시지 배치 (payload: {@code {"eventId":"...", "productId":1}})
     * @param ack      manual ACK
     */
    @KafkaListener(
            topics = TOPIC_PRODUCT_VIEWED,
            containerFactory = KafkaConfig.BATCH_LISTENER
    )
    public void consumeViewEvents(List<ConsumerRecord<String, JsonNode>> messages, Acknowledgment ack) {
        log.debug("[ViewMetrics] 배치 수신: size={}", messages.size());
        for (ConsumerRecord<String, JsonNode> record : messages) {
            try {
                Long productId = record.value().get("productId").asLong();
                productMetricsService.incrementViewCount(productId);
                log.debug("[ViewMetrics] 조회 수 증가: productId={}", productId);
            } catch (Exception e) {
                log.error("[ViewMetrics] 처리 실패: topic={}, offset={}", record.topic(), record.offset(), e);
            }
        }
        ack.acknowledge();
    }
}
