package com.loopers.application.product;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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
            try {
                int totalDeleted = 0;
                int deleted;

                do {
                    deleted = productService.softDeleteByBrandIdInBatch(brandId, BATCH_SIZE);
                    totalDeleted += deleted;
                } while (deleted == BATCH_SIZE);

                if (totalDeleted > 0) {
                    log.info("브랜드 {} 상품 {}개 정리 완료", brandId, totalDeleted);
                }
            } catch (Exception e) {
                log.error("브랜드 {} 상품 정리 실패", brandId, e);
            }
        }
    }
}
