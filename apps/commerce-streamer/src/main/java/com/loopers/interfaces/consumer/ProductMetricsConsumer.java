package com.loopers.interfaces.consumer;

import java.util.ArrayList;
import java.util.List;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.loopers.application.metrics.MetricsEventMeta;
import com.loopers.application.metrics.ProductMetricsService;
import com.loopers.confg.kafka.KafkaConfig;
import com.loopers.domain.metrics.MetricsEventType;
import com.loopers.domain.metrics.MetricsPayload;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 상품 지표 집계를 위한 Kafka Consumer.
 *
 * <p>좋아요/주문 완료/상품 조회 이벤트를 소비하여 Kafka 메시지를
 * {@link MetricsPayload}로 변환하고, {@link ProductMetricsService}에 위임한다.</p>
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
     * 좋아요 이벤트를 소비하여 상품별 좋아요 수 갱신을 요청한다.
     *
     * <p>{@code like-liked-v1} 토픽이면 LIKED, {@code like-unliked-v1} 토픽이면 UNLIKED로 분류한다.</p>
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
                String eventId = extractEventId(record);
                Long productId = record.value().get("productId").asLong();
                boolean liked = TOPIC_LIKED.equals(record.topic());
                MetricsEventType eventType = liked ? MetricsEventType.LIKED : MetricsEventType.UNLIKED;
                productMetricsService.handleEvent(
                        new MetricsEventMeta(eventId, eventType),
                        new MetricsPayload.Like(productId, liked)
                );
            } catch (Exception e) {
                log.error("[LikeMetrics] 처리 실패: topic={}, offset={}", record.topic(), record.offset(), e);
            }
        }
        ack.acknowledge();
    }

    /**
     * 주문 완료 이벤트를 소비하여 상품별 판매량 갱신을 요청한다.
     *
     * <p>이벤트 payload의 {@code orderItems}에서 상품별 수량을 추출하여
     * {@link MetricsPayload.Order}로 변환한다.</p>
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
                String eventId = extractEventId(record);
                List<MetricsPayload.Order.OrderItem> items = parseOrderItems(record.value());
                productMetricsService.handleEvent(
                        new MetricsEventMeta(eventId, MetricsEventType.ORDER_COMPLETED),
                        new MetricsPayload.Order(items)
                );
            } catch (Exception e) {
                log.error("[OrderMetrics] 처리 실패: topic={}, offset={}", record.topic(), record.offset(), e);
            }
        }
        ack.acknowledge();
    }

    /**
     * 상품 조회 이벤트를 소비하여 조회 수 갱신을 요청한다.
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
                String eventId = extractEventId(record);
                Long productId = record.value().get("productId").asLong();
                productMetricsService.handleEvent(
                        new MetricsEventMeta(eventId, MetricsEventType.PRODUCT_VIEWED),
                        new MetricsPayload.View(productId)
                );
            } catch (Exception e) {
                log.error("[ViewMetrics] 처리 실패: topic={}, offset={}", record.topic(), record.offset(), e);
            }
        }
        ack.acknowledge();
    }

    private String extractEventId(ConsumerRecord<String, JsonNode> record) {
        return record.value().get("eventId").asText();
    }

    private List<MetricsPayload.Order.OrderItem> parseOrderItems(JsonNode value) {
        List<MetricsPayload.Order.OrderItem> items = new ArrayList<>();
        for (JsonNode item : value.get("orderItems")) {
            items.add(
                    new MetricsPayload.Order.OrderItem(
                            item.get("productId").asLong(),
                            item.get("quantity").asLong()
                    )
            );
        }
        return items;
    }
}
