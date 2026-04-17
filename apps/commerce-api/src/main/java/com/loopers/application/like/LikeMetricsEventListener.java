package com.loopers.application.like;

import com.loopers.application.product.ProductService;
import com.loopers.domain.like.event.LikeToggledEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@RequiredArgsConstructor
@Component
public class LikeMetricsEventListener {

    private final ProductService productService;
    private final CacheManager cacheManager;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleLikeMetrics(LikeToggledEvent event) {
        if (event.liked()) {
            productService.incrementLikeCount(event.productId());
        } else {
            productService.decrementLikeCount(event.productId());
        }
        log.info("좋아요 집계 처리: productId={}, liked={}", event.productId(), event.liked());
        evictProductDetailCache(event.productId());
    }

    private void evictProductDetailCache(Long productId) {
        try {
            var cache = cacheManager.getCache("productDetail");
            if (cache != null) {
                cache.evict(productId);
            }
        } catch (Exception e) {
            log.warn("캐시 evict 실패 (productId={}): {}", productId, e.getMessage());
        }
    }
}
