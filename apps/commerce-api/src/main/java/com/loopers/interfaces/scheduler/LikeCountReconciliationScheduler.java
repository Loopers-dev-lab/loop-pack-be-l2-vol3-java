package com.loopers.interfaces.scheduler;

import com.loopers.domain.product.ProductRepository;
import com.loopers.infrastructure.product.ProductCacheManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class LikeCountReconciliationScheduler {

    private final ProductRepository productRepository;
    private final ProductCacheManager productCacheManager;

    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void reconcile() {
        int updated = productRepository.reconcileLikeCountFromMetrics();
        if (updated > 0) {
            productCacheManager.evictAllLists();
            log.info("likeCount reconciliation 완료: {}건 동기화", updated);
        }
    }
}
