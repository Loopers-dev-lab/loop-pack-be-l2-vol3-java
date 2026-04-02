package com.loopers.application.event.listener;

import com.loopers.application.event.LikeCanceledEvent;
import com.loopers.application.event.LikeCreatedEvent;
import com.loopers.infrastructure.cache.LikeCountCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 좋아요 카운트 이벤트 리스너.
 * 좋아요 생성/취소 이벤트 발생 시 Redis 카운터 갱신.
 * Redis 작업만 수행하므로 별도 트랜잭션이 필요 없음.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LikeCountEventListener {

    private final LikeCountCacheService likeCountCacheService;

    /**
     * 좋아요 생성 시 카운터 증가.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleLikeCreated(LikeCreatedEvent event) {
        try {
            Long count = likeCountCacheService.increment(event.productId());
            log.debug("좋아요 카운터 증가: productId={}, count={}", event.productId(), count);
        } catch (Exception e) {
            log.warn("좋아요 카운터 증가 실패 (무시): productId={}", event.productId(), e);
        }
    }

    /**
     * 좋아요 취소 시 카운터 감소.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleLikeCanceled(LikeCanceledEvent event) {
        try {
            Long count = likeCountCacheService.decrement(event.productId());
            log.debug("좋아요 카운터 감소: productId={}, count={}", event.productId(), count);
        } catch (Exception e) {
            log.warn("좋아요 카운터 감소 실패 (무시): productId={}", event.productId(), e);
        }
    }
}