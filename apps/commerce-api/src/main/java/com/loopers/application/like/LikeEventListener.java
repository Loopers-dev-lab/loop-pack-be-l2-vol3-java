package com.loopers.application.like;

import com.loopers.domain.common.event.ProductLikedEvent;
import com.loopers.infrastructure.outbox.OutboxEventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 좋아요 이벤트 리스너 — Outbox 저장 (BEFORE_COMMIT)
 *
 * LikeFacade.likeProduct()의 @Transactional 안에서 이벤트가 발행되고,
 * 이 리스너가 같은 TX의 BEFORE_COMMIT에서 Outbox에 저장한다.
 *
 * 좋아요 수 자체(incrementLikeCount)는 Facade TX에서 즉시 반영.
 * 이 리스너는 product_metrics 집계용 이벤트를 Outbox에 저장.
 */
@Component
public class LikeEventListener {

    private static final Logger log = LoggerFactory.getLogger(LikeEventListener.class);

    private final OutboxEventService outboxEventService;

    public LikeEventListener(OutboxEventService outboxEventService) {
        this.outboxEventService = outboxEventService;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleProductLiked(ProductLikedEvent event) {
        outboxEventService.save(
                "PRODUCT",
                event.productId(),
                event.liked() ? "ProductLikedEvent" : "ProductUnlikedEvent",
                event,
                "catalog-events-v1",
                String.valueOf(event.productId())
        );
        log.info("[LikeEventListener] Outbox 저장 (BEFORE_COMMIT) — productId={}, liked={}",
                event.productId(), event.liked());
    }
}
