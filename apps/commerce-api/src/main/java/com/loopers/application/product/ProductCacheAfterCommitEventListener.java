package com.loopers.application.product;

import com.loopers.application.product.event.ProductDeletedEvent;
import com.loopers.application.product.event.ProductLikeChangedEvent;
import com.loopers.application.product.event.ProductUpdatedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import static org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT;

/**
 * 캐시 무효화는 비즈니스 커밋 성공 이후에만 의미가 있으므로 AFTER_COMMIT에서 처리한다.
 * (Step 1: TransactionSynchronization.afterCommit 패턴을 ApplicationEvent로 통일)
 */
@Component
public class ProductCacheAfterCommitEventListener {

    private final ProductCacheService productCacheService;

    public ProductCacheAfterCommitEventListener(ProductCacheService productCacheService) {
        this.productCacheService = productCacheService;
    }

    @TransactionalEventListener(phase = AFTER_COMMIT)
    public void on(ProductLikeChangedEvent event) {
        productCacheService.evictDetail(event.productId());
        productCacheService.evictList();
    }

    @TransactionalEventListener(phase = AFTER_COMMIT)
    public void on(ProductUpdatedEvent event) {
        productCacheService.evictDetail(event.productId());
        productCacheService.evictList();
    }

    @TransactionalEventListener(phase = AFTER_COMMIT)
    public void on(ProductDeletedEvent event) {
        productCacheService.evictDetail(event.productId());
        productCacheService.evictList();
    }
}

