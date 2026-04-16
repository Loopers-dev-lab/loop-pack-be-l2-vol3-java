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
public class OrderEventConsumer {

    private static final String TOPIC = "order-events";
    private static final double ORDER_WEIGHT = 0.7;

    private final ProductMetricsJpaRepository productMetricsJpaRepository;
    private final EventHandledJpaRepository eventHandledJpaRepository;
    private final RankingRedisRepository rankingRedisRepository;

    /**
     * order-events 토픽의 주문 이벤트를 소비하여 product_metrics.order_count를 갱신한다.
     * - 수동 커밋(Acknowledgment): 처리 완료 후에만 오프셋 커밋 → 실패 시 재처리 보장
     * - key=orderId: 같은 주문 이벤트가 동일 파티션 순서로 처리됨
     * - EventHandled: 동일 eventId 중복 처리 방지
     */
    @Transactional
    @KafkaListener(topics = TOPIC, containerFactory = KafkaConfig.BATCH_LISTENER)
    public void handleOrderEvents(
        List<ConsumerRecord<String, OrderEventMessage>> messages,
        Acknowledgment acknowledgment
    ) {
        for (ConsumerRecord<String, OrderEventMessage> record : messages) {
            OrderEventMessage message = record.value();
            if (message == null) {
                continue;
            }
            try {
                LocalDate eventDate = Instant.ofEpochMilli(record.timestamp())
                    .atZone(ZoneId.of("Asia/Seoul"))
                    .toLocalDate();
                processOrderEvent(message, eventDate);
            } catch (ObjectOptimisticLockingFailureException e) {
                log.warn("OrderEvent 낙관적 락 충돌 — 중복 처리 감지, skip: productId={}, eventId={}", message.productId(), message.eventId());
            } catch (Exception e) {
                log.error("OrderEvent 처리 실패: productId={}, type={}", message.productId(), message.type(), e);
            }
        }
        acknowledgment.acknowledge();
    }

    private void processOrderEvent(OrderEventMessage message, LocalDate eventDate) {
        if (message.eventId() != null && eventHandledJpaRepository.existsByTopicAndEventId(TOPIC, message.eventId())) {
            log.debug("OrderEvent 이미 처리됨 — skip: eventId={}", message.eventId());
            return;
        }

        if (!"ORDER_CREATED".equals(message.type())) {
            log.warn("알 수 없는 OrderEvent 타입: {}", message.type());
            return;
        }

        ProductMetrics metrics = productMetricsJpaRepository
            .findByProductIdAndDate(message.productId(), eventDate)
            .orElseGet(() -> new ProductMetrics(message.productId(), eventDate, 0));

        metrics.increaseOrderCount();
        productMetricsJpaRepository.save(metrics);

        if (message.eventId() != null) {
            eventHandledJpaRepository.save(new EventHandled(TOPIC, message.eventId()));
        }

        try {
            double rankingScore = ORDER_WEIGHT * Math.log1p(message.finalAmount());
            rankingRedisRepository.incrementScore(eventDate, message.productId(), rankingScore);
        } catch (Exception e) {
            log.error("OrderEvent 랭킹 ZSET 갱신 실패 (무시): productId={}", message.productId(), e);
        }
    }

    public record OrderEventMessage(String type, Long productId, Long orderId, int finalAmount, String eventId) {
    }
}
