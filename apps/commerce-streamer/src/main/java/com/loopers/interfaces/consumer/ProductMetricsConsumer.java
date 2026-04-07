package com.loopers.interfaces.consumer;

import java.util.List;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import com.loopers.application.metrics.MetricsEventMeta;
import com.loopers.application.metrics.ProductMetricsService;
import com.loopers.confg.kafka.KafkaConfig;
import com.loopers.domain.metrics.MetricsEventType;
import com.loopers.domain.metrics.MetricsPayload;
import com.loopers.interfaces.consumer.dto.MetricsMessageDto.LikeMessage;
import com.loopers.interfaces.consumer.dto.MetricsMessageDto.OrderCompletedMessage;
import com.loopers.interfaces.consumer.dto.MetricsMessageDto.ProductViewedMessage;
import com.loopers.interfaces.consumer.support.KafkaMessageParser;

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


    private final ProductMetricsService productMetricsService;
    private final KafkaMessageParser kafkaMessageParser;

    @KafkaListener(
            topics = {Topics.LIKED, Topics.UNLIKED},
            containerFactory = KafkaConfig.BATCH_LISTENER
    )
    public void consumeLikeEvents(List<ConsumerRecord<String, Object>> messages, Acknowledgment ack) {
        log.debug("[LikeMetrics] 배치 수신: size={}", messages.size());
        for (ConsumerRecord<String, Object> record : messages) {
            try {
                LikeMessage msg = kafkaMessageParser.parse(record.value(), LikeMessage.class);
                boolean liked = Topics.LIKED.equals(record.topic());
                MetricsEventType eventType = liked ? MetricsEventType.LIKED : MetricsEventType.UNLIKED;
                productMetricsService.handleEvent(
                        new MetricsEventMeta(msg.eventId(), eventType),
                        new MetricsPayload.Like(msg.productId(), liked)
                );
            } catch (Exception e) {
                log.error("[LikeMetrics] 처리 실패: topic={}, offset={}", record.topic(), record.offset(), e);
            }
        }
        ack.acknowledge();
    }

    @KafkaListener(
            topics = Topics.ORDER_COMPLETED,
            containerFactory = KafkaConfig.BATCH_LISTENER
    )
    public void consumeOrderEvents(List<ConsumerRecord<String, Object>> messages, Acknowledgment ack) {
        log.debug("[OrderMetrics] 배치 수신: size={}", messages.size());
        for (ConsumerRecord<String, Object> record : messages) {
            try {
                OrderCompletedMessage msg = kafkaMessageParser.parse(record.value(), OrderCompletedMessage.class);
                List<MetricsPayload.Order.OrderItem> items = msg.orderItems().stream()
                        .map(item -> new MetricsPayload.Order.OrderItem(item.productId(), item.quantity(), item.price()))
                        .toList();
                productMetricsService.handleEvent(
                        new MetricsEventMeta(msg.eventId(), MetricsEventType.ORDER_COMPLETED),
                        new MetricsPayload.Order(items)
                );
            } catch (Exception e) {
                log.error("[OrderMetrics] 처리 실패: topic={}, offset={}", record.topic(), record.offset(), e);
            }
        }
        ack.acknowledge();
    }

    @KafkaListener(
            topics = Topics.PRODUCT_VIEWED,
            containerFactory = KafkaConfig.BATCH_LISTENER
    )
    public void consumeViewEvents(List<ConsumerRecord<String, Object>> messages, Acknowledgment ack) {
        log.debug("[ViewMetrics] 배치 수신: size={}", messages.size());
        for (ConsumerRecord<String, Object> record : messages) {
            try {
                ProductViewedMessage msg = kafkaMessageParser.parse(record.value(), ProductViewedMessage.class);
                productMetricsService.handleEvent(
                        new MetricsEventMeta(msg.eventId(), MetricsEventType.PRODUCT_VIEWED),
                        new MetricsPayload.View(msg.productId())
                );
            } catch (Exception e) {
                log.error("[ViewMetrics] 처리 실패: topic={}, offset={}", record.topic(), record.offset(), e);
            }
        }
        ack.acknowledge();
    }
}
