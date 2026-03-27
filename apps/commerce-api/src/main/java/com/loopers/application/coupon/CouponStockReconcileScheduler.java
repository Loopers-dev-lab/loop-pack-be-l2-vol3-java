package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponStockRepository;
import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Redis ↔ DB 쿠폰 재고 보정 스케줄러.
 * Consumer 크래시 등으로 Redis와 DB 사이에 정합성 차이가 발생했을 때,
 * DB(source of truth)의 currentIssuedCount 기준으로 Redis를 동기화한다.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class CouponStockReconcileScheduler {

    private final CouponTemplateRepository couponTemplateRepository;
    private final CouponStockRepository couponStockRepository;

    @Scheduled(fixedDelay = 60_000)  // 1분마다
    public void reconcile() {
        List<CouponTemplate> templates = couponTemplateRepository.findAllWithMaxIssueCount();
        for (CouponTemplate template : templates) {
            int remaining = template.getRemainingIssueCount();
            couponStockRepository.setStock(template.getId(), remaining);
        }
        if (!templates.isEmpty()) {
            log.debug("[쿠폰 재고 보정] {}개 템플릿 Redis 동기화 완료", templates.size());
        }
    }
}
