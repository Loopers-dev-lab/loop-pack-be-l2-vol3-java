package com.loopers.application.coupon;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.domain.coupon.CouponStockManager;
import com.loopers.domain.coupon.OwnedCouponRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Redis 쿠폰 재고를 DB 기준으로 보정하는 스케줄러.
 *
 * <p>매일 새벽 4시에 실행되어 활성 쿠폰의 Redis 상태를
 * DB 실제 발급 데이터 기준으로 동기화한다.
 * Redis 장애 복구 후 데이터 유실에 대한 안전망 역할을 한다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponStockSyncScheduler {

    private final CouponRepository couponRepository;
    private final OwnedCouponRepository ownedCouponRepository;
    private final CouponStockManager couponStockManager;

    /**
     * 활성 쿠폰의 Redis 재고를 DB 기준으로 동기화한다.
     */
    @Scheduled(cron = "0 0 4 * * *")
    public void syncAll() {
        List<Coupon> activeCoupons = couponRepository.findAllActive();

        if (activeCoupons.isEmpty()) {
            return;
        }

        log.info("[CouponStockSync] 보정 시작: 활성 쿠폰 {}건", activeCoupons.size());

        for (Coupon coupon : activeCoupons) {
            try {
                syncCoupon(coupon);
            } catch (Exception e) {
                log.error("[CouponStockSync] 보정 실패: couponId={}", coupon.getId(), e);
            }
        }

        log.info("[CouponStockSync] 보정 완료");
    }

    private void syncCoupon(Coupon coupon) {
        long issuedCount = ownedCouponRepository.countByCouponId(coupon.getId());
        Set<Long> userIds = new HashSet<>(ownedCouponRepository.findUserIdsByCouponId(coupon.getId()));

        int stock = coupon.remainingStock(issuedCount);
        couponStockManager.sync(coupon.getId(), stock, userIds, coupon.getExpiredAt());
    }
}
