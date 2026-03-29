package com.loopers.application.coupon;

import com.loopers.domain.coupon.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponIssueTxService {

    private final CouponRepository couponRepository;
    private final CouponIssueRepository couponIssueRepository;
    private final CouponIssueRequestRepository couponIssueRequestRepository;
    private final CouponService couponService;

    @Transactional
    public void tryIssue(Long requestId) {
        CouponIssueRequest request = couponIssueRequestRepository.findById(requestId)
            .orElse(null);
        if (request == null) {
            log.warn("쿠폰 발급 요청을 찾을 수 없음 - requestId: {}", requestId);
            return;
        }

        Coupon coupon = couponRepository.findById(request.getCouponId()).orElse(null);
        if (coupon == null) {
            request.fail("쿠폰을 찾을 수 없음");
            couponIssueRequestRepository.save(request);
            return;
        }

        // 중복 발급 체크
        if (couponIssueRepository.findByCouponIdAndUserId(request.getCouponId(), request.getUserId()).isPresent()) {
            request.fail("중복 발급 요청");
            couponIssueRequestRepository.save(request);
            return;
        }

        // 수량 제한 체크 (원자적 증가)
        if (coupon.getMaxQuantity() != null) {
            int updated = couponRepository.incrementIssuedQuantity(coupon.getId(), coupon.getMaxQuantity());
            if (updated == 0) {
                request.fail("수량 소진");
                couponIssueRequestRepository.save(request);
                return;
            }
        }

        // 발급
        CouponIssue issue = couponService.createIssue(
            request.getCouponId(), request.getUserId(), coupon.getExpiredAt()
        );
        couponIssueRepository.save(issue);
        request.success();
        couponIssueRequestRepository.save(request);

        log.info("쿠폰 발급 성공 - requestId: {}, couponId: {}, userId: {}",
            requestId, request.getCouponId(), request.getUserId());
    }

    @Transactional
    public void markFailed(Long requestId, String reason) {
        couponIssueRequestRepository.findById(requestId).ifPresent(request -> {
            request.fail(reason);
            couponIssueRequestRepository.save(request);
        });
    }
}
