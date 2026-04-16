package com.loopers.interfaces.consumer;

import com.loopers.confg.kafka.KafkaConfig;
import com.loopers.domain.event.EventHandled;
import com.loopers.domain.product.ProductMetrics;
import com.loopers.infrastructure.event.EventHandledJpaRepository;
import com.loopers.infrastructure.product.ProductMetricsJpaRepository;
import com.loopers.infrastructure.ranking.RankingRedisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Component
public class LikeEventConsumer {

    private static final String TOPIC = "catalog-events";
    private static final double LIKE_WEIGHT = 0.2;

    private final ProductMetricsJpaRepository productMetricsJpaRepository;
    private final EventHandledJpaRepository eventHandledJpaRepository;
    private final RankingRedisRepository rankingRedisRepository;

    /**
     * catalog-events 토픽의 좋아요 이벤트를 소비하여 product_metrics.like_count를 갱신한다.
     * - 수동 커밋(Acknowledgment): 처리 완료 후에만 오프셋 커밋 → 실패 시 재처리 보장
     * - key=productId: 같은 상품 이벤트가 동일 파티션 순서로 처리됨
     * - EventHandled: 동일 eventId 중복 처리 방지
     */
    @Transactional
    @KafkaListener(topics = TOPIC, containerFactory = KafkaConfig.BATCH_LISTENER)
    public void handleLikeEvents(
        List<ConsumerRecord<String, LikeEventMessage>> messages,
        Acknowledgment acknowledgment
    ) {
        for (ConsumerRecord<String, LikeEventMessage> record : messages) {
            LikeEventMessage message = record.value();
            if (message == null) {
                continue;
            }
            try {
                LocalDate eventDate = Instant.ofEpochMilli(record.timestamp())
                    .atZone(ZoneId.of("Asia/Seoul"))
                    .toLocalDate();
                processLikeEvent(message, eventDate);
            } catch (ObjectOptimisticLockingFailureException e) {
                log.warn("LikeEvent 낙관적 락 충돌 — 중복 처리 감지, skip: productId={}, eventId={}",
                    message.productId(), message.eventId());
            } catch (Exception e) {
                log.error("LikeEvent 처리 실패: productId={}, type={}", message.productId(), message.type(), e);
            }
        }
        acknowledgment.acknowledge();
    }

    private void processLikeEvent(LikeEventMessage message, LocalDate eventDate) {
        if (message.eventId() != null && eventHandledJpaRepository.existsByTopicAndEventId(TOPIC, message.eventId())) {
            log.debug("LikeEvent 이미 처리됨 — skip: eventId={}", message.eventId());
            return;
        }

        ProductMetrics metrics = productMetricsJpaRepository
            .findByProductIdAndDate(message.productId(), eventDate)
            .orElseGet(() -> new ProductMetrics(message.productId(), eventDate, 0));

        double rankingIncrement;
        if ("LIKED".equals(message.type())) {
            metrics.increaseLikeCount();
            rankingIncrement = LIKE_WEIGHT;
        } else if ("UNLIKED".equals(message.type())) {
            metrics.decreaseLikeCount();
            rankingIncrement = -LIKE_WEIGHT;
        } else {
            log.warn("알 수 없는 LikeEvent 타입: {}", message.type());
            return;
        }

        productMetricsJpaRepository.save(metrics);

        if (message.eventId() != null) {
            eventHandledJpaRepository.save(new EventHandled(TOPIC, message.eventId()));
        }

        try {
            rankingRedisRepository.incrementScore(eventDate, message.productId(), rankingIncrement);
        } catch (Exception e) {
            log.error("LikeEvent 랭킹 ZSET 갱신 실패 (무시): productId={}", message.productId(), e);
        }
    }

    public record LikeEventMessage(String type, Long productId, Long userId, String eventId) {
    }
}
