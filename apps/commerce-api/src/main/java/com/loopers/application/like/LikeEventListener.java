package com.loopers.application.like;

import com.loopers.domain.common.event.ProductLikedEvent;
import com.loopers.domain.common.event.UserActivityEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 좋아요 이벤트 리스너
 *
 * 좋아요 TX 커밋 이후:
 *   - product_metrics 집계 (eventual consistency)
 *   - 유저 행동 로깅
 *
 * 좋아요 수 자체(incrementLikeCount)는 같은 TX에서 처리.
 * 여기서 하는 건 "집계 테이블에 반영"과 "행동 로깅".
 *
 * Step 2에서 Kafka catalog-events 토픽으로 전환 예정.
 */
@Component
public class LikeEventListener {

    private static final Logger log = LoggerFactory.getLogger(LikeEventListener.class);

    /**
     * 좋아요 → product_metrics 집계
     *
     * 현재는 로그만 남기고, Step 2에서 Kafka Consumer가
     * product_metrics 테이블에 upsert하는 구조로 전환.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleProductLiked(ProductLikedEvent event) {
        log.info("[LikeEventListener] product_metrics 집계 예정 — productId={}, liked={}, userId={}",
                event.productId(), event.liked(), event.userId());
        // Step 2: Kafka catalog-events-v1 토픽으로 전환
        // Consumer가 product_metrics.like_count를 upsert
    }

    /**
     * 좋아요 → 유저 행동 로깅
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleLikeActivity(ProductLikedEvent event) {
        log.info("[LikeEventListener] 유저 행동 로깅 — userId={}, productId={}, type=LIKE",
                event.userId(), event.productId());
        // Step 2: Kafka user-activity-events-v1 토픽으로 전환
    }
}
