package com.loopers.interfaces.listener;

import com.loopers.application.product.ProductCachePort;
import com.loopers.domain.event.LikeCreatedEvent;
import com.loopers.domain.event.LikeRemovedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class CacheEvictionEventListener {

    private final ProductCachePort productCachePort;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleLikeCreated(LikeCreatedEvent event) {
        try {
            productCachePort.evictProductDetail(event.productId());
            productCachePort.evictProductList();
        } catch (Exception e) {
            log.warn("캐시 무효화 실패 — best-effort", e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleLikeRemoved(LikeRemovedEvent event) {
        try {
            productCachePort.evictProductDetail(event.productId());
            productCachePort.evictProductList();
        } catch (Exception e) {
            log.warn("캐시 무효화 실패 — best-effort", e);
        }
    }
}
