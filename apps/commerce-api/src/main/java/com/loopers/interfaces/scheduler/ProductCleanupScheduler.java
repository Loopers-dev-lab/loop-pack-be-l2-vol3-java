package com.loopers.interfaces.scheduler;

import com.loopers.application.product.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductCleanupScheduler {

    private static final int BATCH_SIZE = 100;

    private final ProductService productService;

    @Scheduled(cron = "0 0 3 * * *")
    public void cleanup() {
        List<Long> brandIds = productService.findBrandIdsWithUncleanedProducts();

        for (Long brandId : brandIds) {
            try {
                int totalDeleted = 0;

                while (true) {
                    List<Long> ids = productService.findIdsForCleanup(brandId, BATCH_SIZE);
                    if (ids.isEmpty()) break;

                    int deleted = productService.softDeleteByIds(ids);
                    totalDeleted += deleted;

                    if (ids.size() < BATCH_SIZE) break;
                    Thread.sleep(100);
                }

                if (totalDeleted > 0) {
                    log.info("브랜드 {} 상품 {}개 정리 완료", brandId, totalDeleted);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("브랜드 {} 상품 정리 중 인터럽트 발생", brandId);
            } catch (Exception e) {
                log.error("브랜드 {} 상품 정리 실패", brandId, e);
            }
        }
    }
}
