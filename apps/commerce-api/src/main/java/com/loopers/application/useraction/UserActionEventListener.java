package com.loopers.application.useraction;

import com.loopers.domain.useraction.UserActionEvent;
import com.loopers.infrastructure.outbox.OutboxMessage;
import com.loopers.infrastructure.outbox.OutboxTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

/**
 * 유저 행동 이벤트 리스너.
 *
 * PRODUCT_VIEW 이벤트는 Kafka(catalog-events)로 직접 발행한다.
 * Outbox 미사용 — 조회는 weight 0.1로 낮아 유실 허용. 앱 크래시 시 이벤트 유실 감수.
 * eventId는 UUID 기반 Long으로 생성 — Kafka at-least-once 재전송 시 동일 메시지 바이트로
 * EventHandled 멱등성 체크가 작동한다.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class UserActionEventListener {

    private final KafkaTemplate<Object, Object> kafkaTemplate;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleUserAction(UserActionEvent event) {
        log.info("[유저 행동] action={}, userId={}, targetType={}, targetId={}, metadata={}",
                event.actionType(), event.userId(), event.targetType(), event.targetId(), event.metadata());

        if (event.actionType() == UserActionEvent.ActionType.PRODUCT_VIEW) {
            publishProductViewedEvent(event.targetId());
        }
    }

    private void publishProductViewedEvent(Long productId) {
        try {
            long eventId = UUID.randomUUID().getLeastSignificantBits();
            String payload = "{\"productId\":" + productId + "}";
            OutboxMessage message = new OutboxMessage(eventId, "PRODUCT", productId, "PRODUCT_VIEWED", payload);
            kafkaTemplate.send(OutboxTopics.CATALOG_EVENTS, String.valueOf(productId), message);
            log.debug("[VIEW 이벤트 발행] productId={}", productId);
        } catch (Exception e) {
            // 유실 허용 — VIEW 이벤트는 weight 0.1로 낮아 발행 실패 시 로그만 남기고 계속 진행
            log.warn("[VIEW 이벤트 발행 실패] productId={}, error={}", productId, e.getMessage());
        }
    }
}
