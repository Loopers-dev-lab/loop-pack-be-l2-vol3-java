package com.loopers.application.product;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductScheduler {

    private static final int BATCH_SIZE = 100;

    private final ProductService productService;

    @Scheduled(cron = "0 0 3 * * *")
    public void cleanup() {
        List<Long> brandIds = productService.findBrandIdsWithUncleanedProducts();

        for (Long brandId : brandIds) {
            int totalDeleted = 0;
            int deleted;

            do {
                deleted = deleteInBatch(brandId);
                totalDeleted += deleted;
            } while (deleted == BATCH_SIZE);

            if (totalDeleted > 0) {
                log.info("브랜드 {} 상품 {}개 정리 완료", brandId, totalDeleted);
            }
        }
    }

    @Transactional
    public int deleteInBatch(Long brandId) {
        return productService.softDeleteByBrandIdInBatch(brandId, BATCH_SIZE);
    }
}
