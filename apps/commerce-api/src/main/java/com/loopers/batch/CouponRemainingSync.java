package com.loopers.batch;

import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 선착순 쿠폰 잔여 수량 동기화 스케줄러.
 * <p>
 * 1시간 간격으로 DB의 실제 잔여 수량(max_quantity - issued_count)을
 * Redis remaining 카운터에 동기화한다.
 * Redis 장애 복구 또는 카운터 드리프트 보정을 위해 사용된다.
 * 불필요한 Redis 쓰기를 방지하기 위해 값이 다를 때만 갱신한다.
 * </p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CouponRemainingSync {

    private static final String REMAINING_KEY_PREFIX = "coupon:remaining:";

    private final CouponRepository couponRepository;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * DB 기준으로 Redis 잔여 수량을 동기화한다 (1시간 간격).
     * 값이 일치하면 Redis 쓰기를 건너뛴다.
     */
    @Scheduled(fixedDelay = 3600000)
    public void syncRemainingFromDb() {
        List<CouponModel> rushCoupons = couponRepository.findAll().stream()
                .filter(CouponModel::isRushCoupon)
                .filter(c -> !c.isDeleted())
                .toList();

        if (rushCoupons.isEmpty()) {
            return;
        }

        int syncCount = 0;
        for (CouponModel coupon : rushCoupons) {
            try {
                int remaining = coupon.getMaxQuantity() - coupon.getIssuedCount();
                String expectedValue = String.valueOf(Math.max(remaining, 0));
                String key = REMAINING_KEY_PREFIX + coupon.getCouponId();
                String currentValue = stringRedisTemplate.opsForValue().get(key);

                if (!expectedValue.equals(currentValue)) {
                    stringRedisTemplate.opsForValue().set(key, expectedValue);
                    syncCount++;
                }
            } catch (Exception e) {
                log.warn("쿠폰 잔여 수량 동기화 실패: couponId={}", coupon.getCouponId(), e);
            }
        }
        log.info("쿠폰 잔여 수량 동기화 완료: {}/{}", syncCount, rushCoupons.size());
    }
}
