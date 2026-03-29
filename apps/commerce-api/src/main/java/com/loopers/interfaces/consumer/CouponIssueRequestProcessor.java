package com.loopers.interfaces.consumer;

import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponIssueMessage;
import com.loopers.domain.coupon.CouponIssueRequest;
import com.loopers.domain.coupon.CouponIssueRequestRepository;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.UserCoupon;
import com.loopers.domain.coupon.UserCouponRepository;
import com.loopers.support.error.CoreException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Component
public class CouponIssueRequestProcessor {

    private final CouponIssueRequestRepository couponIssueRequestRepository;
    private final CouponService couponService;
    private final UserCouponRepository userCouponRepository;

    @Transactional
    public void process(CouponIssueMessage message) {
        CouponIssueRequest request = couponIssueRequestRepository.findByRequestId(message.requestId())
            .orElseThrow(() -> new IllegalStateException("발급 요청을 찾을 수 없음. requestId=" + message.requestId()));

        Coupon coupon;
        try {
            coupon = couponService.getCouponForIssue(message.couponId());
        } catch (CoreException e) {
            request.markFailed("쿠폰을 찾을 수 없거나 만료됨");
            couponIssueRequestRepository.save(request);
            return;
        }

        // TODO [Q2-B]: 현재 DB COUNT 쿼리로 선착순 체크 (Kafka 파티션 키 = couponId로 직렬화 보장).
        //   개선 기준: 쿠폰 발급 처리 시간 P99 > 100ms 또는 단일 쿠폰 발급 TPS > 1,000 이상일 때 Redis Counter로 전환.
        //   전환 방법: Kafka 발행 전 CouponFacade에서 Redis DECR 수행 → 반환값 < 0이면 즉시 실패 응답.
        //   테스트: k6/Gatling으로 10,000 동시 요청 후 처리 시간 P99 측정.
        if (coupon.getMaxIssuable() != null) {
            long issuedCount = userCouponRepository.countByCouponId(message.couponId());
            if (issuedCount >= coupon.getMaxIssuable()) {
                request.markFailed("선착순 마감");
                couponIssueRequestRepository.save(request);
                return;
            }
        }

        if (userCouponRepository.existsByUserIdAndCouponId(message.userId(), message.couponId())) {
            request.markFailed("중복 발급");
            couponIssueRequestRepository.save(request);
            return;
        }

        userCouponRepository.save(new UserCoupon(message.userId(), message.couponId(), coupon.getName()));
        request.markSuccess();
        couponIssueRequestRepository.save(request);
    }
}
