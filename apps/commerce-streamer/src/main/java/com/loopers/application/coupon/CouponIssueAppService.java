package com.loopers.application.coupon;

import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.domain.coupon.IssuedCouponRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.NoSuchElementException;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponIssueAppService {
    private final CouponRepository couponRepository;
    private final IssuedCouponRepository issuedCouponRepository;
    private final StringRedisTemplate redisTemplate;
    private final TransactionTemplate transactionTemplate;

    private static final String STATUS_KEY_PREFIX = "coupon:issue:status:";
    private static final Duration STATUS_TTL = Duration.ofMinutes(10);

    public void processCouponIssue(String requestId, Long couponId, Long userId) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                // 비관적 락으로 쿠폰 조회 — 먼저 직렬화해야 중복 체크가 안전
                Coupon coupon = couponRepository.findByIdWithLock(couponId)
                        .orElseThrow(() -> new NoSuchElementException("쿠폰을 찾을 수 없습니다."));

                // 락 획득 후 중복 발급 체크 — TOCTOU 완전 차단
                if (issuedCouponRepository.findByCouponIdAndUserId(couponId, userId).isPresent()) {
                    throw new IllegalStateException("이미 발급된 쿠폰입니다.");
                }

                // 발급 가능 여부 검증 + 수량 차감
                coupon.issue();

                // IssuedCoupon 생성 + 저장
                IssuedCoupon issuedCoupon = IssuedCoupon.create(coupon, userId);
                issuedCouponRepository.save(issuedCoupon);
            });
            // TX 커밋 이후 Redis 업데이트
            updateStatus(requestId, "SUCCESS");
            log.info("쿠폰 발급 성공: requestId={}, couponId={}, userId={}", requestId, couponId, userId);
        } catch (Exception e) {
            updateStatus(requestId, "FAILED:" + e.getMessage());
            log.error("쿠폰 발급 실패: requestId={}, couponId={}, userId={}", requestId, couponId, userId, e);
        }
    }

    private void updateStatus(String requestId, String status) {
        redisTemplate.opsForValue().set(STATUS_KEY_PREFIX + requestId, status, STATUS_TTL);
    }
}
