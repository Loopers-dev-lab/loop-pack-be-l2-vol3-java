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
import com.loopers.interfaces.consumer.dto.MetricsMessageDto.OrderCompletedMessage.OrderItem;
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
    private static final String TOPIC_LIKED = "like-liked-v1";
    private static final String TOPIC_UNLIKED = "like-unliked-v1";
    private static final String TOPIC_ORDER_COMPLETED = "order-completed-v1";
    private static final String TOPIC_PRODUCT_VIEWED = "product-viewed-v1";

    private final RankingService rankingService;
    private final KafkaMessageParser kafkaMessageParser;

    @KafkaListener(
            topics = {TOPIC_LIKED, TOPIC_UNLIKED, TOPIC_ORDER_COMPLETED, TOPIC_PRODUCT_VIEWED},
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

    private void parseRankingEvent(ConsumerRecord<String, Object> record, List<RankingEvent> events) throws Exception {
        switch (record.topic()) {
            case TOPIC_LIKED, TOPIC_UNLIKED -> {
                LikeMessage message = kafkaMessageParser.parse(record.value(), LikeMessage.class);
                boolean liked = TOPIC_LIKED.equals(record.topic());
                events.add(new RankingEvent.Like(message.eventId(), message.productId(), liked));
            }
            case TOPIC_ORDER_COMPLETED -> {
                OrderCompletedMessage message = kafkaMessageParser.parse(record.value(), OrderCompletedMessage.class);
                for (OrderItem item : message.orderItems()) {
                    events.add(new RankingEvent.Order(message.eventId(), item.productId(), item.price(), item.quantity()));
                }
            }
            case TOPIC_PRODUCT_VIEWED -> {
                ProductViewedMessage message = kafkaMessageParser.parse(record.value(), ProductViewedMessage.class);
                events.add(new RankingEvent.View(message.eventId(), message.productId()));
            }
            default -> log.warn("[Ranking] 알 수 없는 토픽: {}", record.topic());
        }
    }
}
