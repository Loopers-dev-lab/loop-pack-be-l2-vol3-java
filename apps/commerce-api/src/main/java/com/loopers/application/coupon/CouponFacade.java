package com.loopers.application.coupon;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.coupon.*;
import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxEventService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CouponFacade {

    private final CouponService couponService;
    private final OutboxEventService outboxEventService;
    private final ObjectMapper objectMapper;

    @Transactional
    public CouponInfo createCoupon(CreateCouponCommand command) {
        Coupon coupon = couponService.createCoupon(command);
        return CouponInfo.from(coupon);
    }

    public CouponInfo getCoupon(Long couponId) {
        Coupon coupon = couponService.getById(couponId);
        return CouponInfo.from(coupon);
    }

    public Page<CouponInfo> getCoupons(Pageable pageable) {
        return couponService.getAll(pageable).map(CouponInfo::from);
    }

    @Transactional
    public CouponInfo updateCoupon(Long couponId, UpdateCouponCommand command) {
        Coupon coupon = couponService.updateCoupon(couponId, command);
        return CouponInfo.from(coupon);
    }

    @Transactional
    public void deleteCoupon(Long couponId) {
        couponService.deleteCoupon(couponId);
    }

    @Transactional
    public CouponIssueResultInfo requestCouponIssue(Long userId, Long couponId) {
        Coupon coupon = couponService.getById(couponId);
        if (coupon.isExpired()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰은 발급할 수 없습니다.");
        }

        CouponIssueResult issueResult = couponService.createIssueResult(userId, couponId);
        saveOutboxEvent(userId, couponId);
        return CouponIssueResultInfo.from(issueResult);
    }

    public CouponIssueResultInfo getCouponIssueResult(Long userId, Long couponId) {
        CouponIssueResult result = couponService.getIssueResult(userId, couponId);
        return CouponIssueResultInfo.from(result);
    }

    @Transactional
    public UserCouponInfo issueCoupon(Long userId, Long couponId) {
        UserCoupon userCoupon = couponService.issueCoupon(userId, couponId);
        return UserCouponInfo.from(userCoupon);
    }

    private void saveOutboxEvent(Long userId, Long couponId) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of("userId", userId, "couponId", couponId));
            OutboxEvent outboxEvent = OutboxEvent.create("coupon-issue-requests", "COUPON_ISSUE_REQUESTED",
                    String.valueOf(couponId), payload);
            outboxEventService.save(outboxEvent);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Outbox 이벤트 직렬화 실패", e);
        }
    }

    public List<UserCouponInfo> getUserCoupons(Long userId) {
        List<UserCoupon> userCoupons = couponService.getUserCoupons(userId);
        return userCoupons.stream()
                .map(uc -> {
                    Coupon coupon = couponService.getById(uc.getCouponId());
                    return UserCouponInfo.from(uc, coupon);
                })
                .toList();
    }

    public Page<UserCouponInfo> getIssuedCoupons(Long couponId, Pageable pageable) {
        return couponService.getIssuedCoupons(couponId, pageable)
                .map(UserCouponInfo::from);
    }
}
