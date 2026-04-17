package com.loopers.application.event;

import com.loopers.application.product.ProductService;
import com.loopers.domain.event.ProductLikedEvent;
import com.loopers.domain.event.ProductUnlikedEvent;
import com.loopers.infrastructure.product.ProductCacheManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class LikeEventHandler {

    private final ProductService productService;
    private final ProductCacheManager productCacheManager;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void handleLiked(ProductLikedEvent event) {
        try {
            productService.incrementLikeCount(event.productId());
            productCacheManager.evictDetail(event.productId());
        } catch (Exception e) {
            log.error("좋아요 집계 실패: productId={}", event.productId(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void handleUnliked(ProductUnlikedEvent event) {
        try {
            productService.decrementLikeCountIfPositive(event.productId());
            productCacheManager.evictDetail(event.productId());
        } catch (Exception e) {
            log.error("좋아요 취소 집계 실패: productId={}", event.productId(), e);
        }
    }
}
