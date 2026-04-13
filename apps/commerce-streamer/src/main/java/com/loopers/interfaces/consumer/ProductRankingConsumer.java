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
 * <p>일간({@code commerce-streamer-ranking})과 시간 단위({@code commerce-streamer-hourly-ranking})
 * 두 consumer group으로 동일 토픽을 독립적으로 소비한다.
 * 배치 단위로 {@link RankingService}에 위임하여 각각의 Redis Sorted Set에 적재한다.</p>
 *
 * <p>랭킹 적재 실패 시 로그만 남기고 ACK은 정상 수행한다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductRankingConsumer {

    private static final String DAILY_GROUP_ID = "commerce-streamer-ranking";
    private static final String HOURLY_GROUP_ID = "commerce-streamer-hourly-ranking";

    private final RankingService rankingService;
    private final KafkaMessageParser kafkaMessageParser;

    @KafkaListener(
            topics = {Topics.LIKED, Topics.UNLIKED, Topics.ORDER_COMPLETED, Topics.PRODUCT_VIEWED},
            groupId = DAILY_GROUP_ID,
            containerFactory = KafkaConfig.BATCH_LISTENER
    )
    public void consumeDailyRankingEvents(List<ConsumerRecord<String, Object>> messages, Acknowledgment ack) {
        List<RankingEvent> events = parseRankingEvents(messages, "DailyRanking");
        tryExecute(() -> rankingService.processDailyBatch(events), "DailyRanking");
        ack.acknowledge();
    }

    @KafkaListener(
            topics = Topics.PRODUCT_DELETED,
            groupId = DAILY_GROUP_ID,
            containerFactory = KafkaConfig.BATCH_LISTENER
    )
    public void consumeDailyDeletedEvents(List<ConsumerRecord<String, Object>> messages, Acknowledgment ack) {
        List<RankingEvent.Delete> events = parseDeleteEvents(messages, "DailyRanking:Delete");
        tryExecute(() -> rankingService.removeDailyProducts(events), "DailyRanking:Delete");
        ack.acknowledge();
    }

    @KafkaListener(
            topics = {Topics.LIKED, Topics.UNLIKED, Topics.ORDER_COMPLETED, Topics.PRODUCT_VIEWED},
            groupId = HOURLY_GROUP_ID,
            containerFactory = KafkaConfig.BATCH_LISTENER
    )
    public void consumeHourlyRankingEvents(List<ConsumerRecord<String, Object>> messages, Acknowledgment ack) {
        List<RankingEvent> events = parseRankingEvents(messages, "HourlyRanking");
        tryExecute(() -> rankingService.processHourlyBatch(events), "HourlyRanking");
        ack.acknowledge();
    }

    @KafkaListener(
            topics = Topics.PRODUCT_DELETED,
            groupId = HOURLY_GROUP_ID,
            containerFactory = KafkaConfig.BATCH_LISTENER
    )
    public void consumeHourlyDeletedEvents(List<ConsumerRecord<String, Object>> messages, Acknowledgment ack) {
        List<RankingEvent.Delete> events = parseDeleteEvents(messages, "HourlyRanking:Delete");
        tryExecute(() -> rankingService.removeHourlyProducts(events), "HourlyRanking:Delete");
        ack.acknowledge();
    }

    private List<RankingEvent> parseRankingEvents(List<ConsumerRecord<String, Object>> messages, String label) {
        log.debug("[{}] 배치 수신: size={}", label, messages.size());
        List<RankingEvent> events = new ArrayList<>();
        for (ConsumerRecord<String, Object> record : messages) {
            try {
                parseRankingEvent(record, events);
            } catch (Exception e) {
                log.error("[{}] 파싱 실패: topic={}, offset={}", label, record.topic(), record.offset(), e);
            }
        }
        return events;
    }

    private List<RankingEvent.Delete> parseDeleteEvents(List<ConsumerRecord<String, Object>> messages, String label) {
        log.debug("[{}] 배치 수신: size={}", label, messages.size());
        List<RankingEvent.Delete> events = new ArrayList<>();
        for (ConsumerRecord<String, Object> record : messages) {
            try {
                ProductDeletedMessage msg = kafkaMessageParser.parse(record.value(), ProductDeletedMessage.class);
                events.add(new RankingEvent.Delete(msg.eventId(), msg.productId()));
            } catch (Exception e) {
                log.error("[{}] 파싱 실패: offset={}", label, record.offset(), e);
            }
        }
        return events;
    }

    private void tryExecute(Runnable action, String label) {
        try {
            action.run();
        } catch (Exception e) {
            log.error("[{}] 랭킹 적재 실패", label, e);
        }
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
