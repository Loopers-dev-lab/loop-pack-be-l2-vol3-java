package com.loopers.application.product;

import com.loopers.domain.ranking.ProductRankingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ProductCacheEvictListener {

    private final ProductDetailCacheRepository productDetailCacheRepository;
    private final ProductListCacheRepository productListCacheRepository;
    private final ProductRankingRepository productRankingRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleCacheEvict(ProductCacheEvictEvent event) {
        event.productIds().forEach(productId -> {
            productDetailCacheRepository.evict(productId);
            productRankingRepository.removeProduct(productId);
        });
        if (event.evictList()) {
            productListCacheRepository.evictAll();
        }
    }
}
