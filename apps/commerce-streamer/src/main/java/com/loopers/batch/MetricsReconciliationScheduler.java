package com.loopers.batch;

import com.loopers.domain.metrics.MetricsLikeCountMismatch;
import com.loopers.domain.metrics.ProductMetricsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 상품 지표 보정 배치 스케줄러.
 *
 * <p>매일 06:00 -- product_metrics.like_count를 products.like_count로 보정한다.
 * SSOT: products.like_count (05:00에 likes COUNT(*)로 보정됨).
 * like_count만 갱신, like_version/like_updated_at 유지.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MetricsReconciliationScheduler {

    private final ProductMetricsRepository metricsRepository;

    @Scheduled(cron = "0 0 6 * * *")
    @Transactional
    public void reconcileMetrics() {
        List<MetricsLikeCountMismatch> mismatches = metricsRepository.findLikeCountMismatches();

        if (mismatches.isEmpty()) {
            log.info("[metrics보정] 불일치 없음");
            return;
        }

        log.warn("[metrics보정] 불일치 {}건 감지, 보정 시작", mismatches.size());
        int fixed = 0;
        for (MetricsLikeCountMismatch m : mismatches) {
            try {
                metricsRepository.forceUpdateLikeCount(m.productId(), m.productsCount());
                log.info("[metrics보정] productId={}, metrics={} -> products={}",
                    m.productId(), m.metricsCount(), m.productsCount());
                fixed++;
            } catch (Exception e) {
                log.error("[metrics보정실패] productId={}", m.productId(), e);
            }
        }
        log.info("[metrics보정] 완료: {}건 보정 / {}건 감지", fixed, mismatches.size());
    }
}
