package com.loopers.interfaces.consumer;

import java.util.ArrayList;
import java.util.List;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import com.loopers.application.ranking.RankingService;
import com.loopers.confg.kafka.KafkaConfig;
import com.loopers.domain.ranking.RankingEvent;
import com.loopers.interfaces.consumer.dto.MetricsMessageDto.LikeMessage;
import com.loopers.interfaces.consumer.dto.MetricsMessageDto.OrderCompletedMessage;
import com.loopers.interfaces.consumer.dto.MetricsMessageDto.ProductDeletedMessage;
import com.loopers.interfaces.consumer.dto.MetricsMessageDto.ProductViewedMessage;
import com.loopers.interfaces.consumer.support.KafkaMessageParser;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 상품 랭킹 적재를 위한 Kafka Consumer.
 *
 * <p>별도 consumer group({@code commerce-streamer-ranking})으로
 * 메트릭스 Consumer와 독립적으로 동일 토픽을 소비한다.
 * 단일 리스너로 모든 이벤트 토픽을 수신하여 배치 단위로
 * {@link RankingService}에 위임한다.</p>
 *
 * <p>랭킹 적재 실패 시 로그만 남기고 ACK은 정상 수행한다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductRankingConsumer {

    private static final String GROUP_ID = "commerce-streamer-ranking";

    private final RankingService rankingService;
    private final KafkaMessageParser kafkaMessageParser;

    @KafkaListener(
            topics = {Topics.LIKED, Topics.UNLIKED, Topics.ORDER_COMPLETED, Topics.PRODUCT_VIEWED},
            groupId = GROUP_ID,
            containerFactory = KafkaConfig.BATCH_LISTENER
    )
    public void consumeRankingEvents(List<ConsumerRecord<String, Object>> messages, Acknowledgment ack) {
        log.debug("[Ranking] 배치 수신: size={}", messages.size());
        List<RankingEvent> rankingEvents = new ArrayList<>();

        for (ConsumerRecord<String, Object> record : messages) {
            try {
                parseRankingEvent(record, rankingEvents);
            } catch (Exception e) {
                log.error("[Ranking] 파싱 실패: topic={}, offset={}", record.topic(), record.offset(), e);
            }
        }

        try {
            rankingService.processBatch(rankingEvents);
        } catch (Exception e) {
            log.error("[Ranking] 랭킹 적재 실패", e);
        }
        ack.acknowledge();
    }

    @KafkaListener(
            topics = Topics.PRODUCT_DELETED,
            groupId = GROUP_ID,
            containerFactory = KafkaConfig.BATCH_LISTENER
    )
    public void consumeProductDeletedEvents(List<ConsumerRecord<String, Object>> messages, Acknowledgment ack) {
        log.debug("[Ranking:Delete] 배치 수신: size={}", messages.size());
        List<RankingEvent.Delete> deleteEvents = new ArrayList<>();

        for (ConsumerRecord<String, Object> record : messages) {
            try {
                ProductDeletedMessage msg = kafkaMessageParser.parse(record.value(), ProductDeletedMessage.class);
                deleteEvents.add(new RankingEvent.Delete(msg.eventId(), msg.productId()));
            } catch (Exception e) {
                log.error("[Ranking:Delete] 파싱 실패: offset={}", record.offset(), e);
            }
        }

        try {
            rankingService.removeProducts(deleteEvents);
        } catch (Exception e) {
            log.error("[Ranking:Delete] 랭킹 제거 실패", e);
        }
        ack.acknowledge();
    }

    private void parseRankingEvent(ConsumerRecord<String, Object> record, List<RankingEvent> events) throws Exception {
        switch (record.topic()) {
            case Topics.LIKED, Topics.UNLIKED -> {
                LikeMessage message = kafkaMessageParser.parse(record.value(), LikeMessage.class);
                boolean liked = Topics.LIKED.equals(record.topic());
                events.add(new RankingEvent.Like(message.eventId(), message.productId(), liked));
            }
            case Topics.ORDER_COMPLETED -> {
                OrderCompletedMessage message = kafkaMessageParser.parse(record.value(), OrderCompletedMessage.class);
                List<RankingEvent.Order.OrderItem> items = message.orderItems().stream()
                        .map(item -> new RankingEvent.Order.OrderItem(item.productId(), item.price(), item.quantity()))
                        .toList();
                if (!items.isEmpty()) {
                    events.add(new RankingEvent.Order(message.eventId(), items));
                }
            }
            case Topics.PRODUCT_VIEWED -> {
                ProductViewedMessage message = kafkaMessageParser.parse(record.value(), ProductViewedMessage.class);
                events.add(new RankingEvent.View(message.eventId(), message.productId()));
            }
            default -> log.warn("[Ranking] 알 수 없는 토픽: {}", record.topic());
        }
    }
}
