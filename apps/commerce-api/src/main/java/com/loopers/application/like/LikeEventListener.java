package com.loopers.application.like;

import com.loopers.domain.common.event.ProductLikedEvent;
import com.loopers.infrastructure.outbox.OutboxEventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 좋아요 이벤트 리스너 — Step 2 (Kafka 전환)
 *
 * Outbox에 저장 → Relay → catalog-events-v1 → commerce-streamer에서 집계
 */
@Component
public class LikeEventListener {

    private static final Logger log = LoggerFactory.getLogger(LikeEventListener.class);

    private final OutboxEventService outboxEventService;

    public LikeEventListener(OutboxEventService outboxEventService) {
        this.outboxEventService = outboxEventService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleProductLiked(ProductLikedEvent event) {
        try {
            outboxEventService.save(
                    "PRODUCT",
                    event.productId(),
                    event.liked() ? "ProductLikedEvent" : "ProductUnlikedEvent",
                    event,
                    "catalog-events-v1",
                    String.valueOf(event.productId())
            );
            log.info("[LikeEventListener] Outbox 저장 — productId={}, liked={}",
                    event.productId(), event.liked());
        } catch (Exception e) {
            log.error("[LikeEventListener] Outbox 저장 실패 — productId={}, error={}",
                    event.productId(), e.getMessage(), e);
        }
    }
}
