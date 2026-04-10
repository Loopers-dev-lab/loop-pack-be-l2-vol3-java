package com.loopers.batch;

import com.loopers.domain.product.LikeCountMismatch;
import com.loopers.domain.product.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 좋아요 수 보정 배치 스케줄러.
 *
 * <p>매일 05:00 -- products.like_count를 likes COUNT(*)로 보정한다.
 * SSOT: likes 테이블. 불일치 감지 후 건별 UPDATE.</p>
 *
 * <p>비동기 이벤트 처리 실패, 동시성 이슈 등으로 발생할 수 있는
 * 좋아요 수 불일치를 주기적으로 보정하는 안전망 역할을 한다.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LikeCountReconciliationScheduler {

    private final ProductRepository productRepository;

    /**
     * 좋아요 수 불일치 상품을 조회하여 보정한다.
     */
    @Scheduled(cron = "0 0 5 * * *")
    @Transactional
    public void reconcileLikeCounts() {
        List<LikeCountMismatch> mismatches = productRepository.findLikeCountMismatches();
        if (mismatches.isEmpty()) {
            log.info("[like_count보정] 불일치 없음");
            return;
        }

        log.warn("[like_count보정] 불일치 {}건 감지, 보정 시작", mismatches.size());
        int fixed = 0;
        for (LikeCountMismatch m : mismatches) {
            try {
                productRepository.updateLikeCount(m.productId(), m.actualCount());
                log.info("[like_count보정] productId={}, {} -> {}", m.productId(), m.currentCount(), m.actualCount());
                fixed++;
            } catch (Exception e) {
                log.error("[like_count보정실패] productId={}", m.productId(), e);
            }
        }
        log.info("[like_count보정] 완료: {}건 보정 / {}건 감지", fixed, mismatches.size());
    }
}
