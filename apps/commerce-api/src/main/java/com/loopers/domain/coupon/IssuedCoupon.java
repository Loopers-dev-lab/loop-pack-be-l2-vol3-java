package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.LocalDateTime;
import java.util.UUID;

public record IssuedCoupon(
        String memberId,
        UUID couponId,
        CouponStatus status,
        LocalDateTime issuedAt,
        LocalDateTime expiredAt,
        LocalDateTime usedAt
) {
    public IssuedCoupon {
        if (memberId == null || memberId.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "회원 식별자는 필수입니다.");
        }
        if (couponId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 식별자는 필수입니다.");
        }
        if (status == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 상태는 필수입니다.");
        }
        if (issuedAt == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "발급 시각은 필수입니다.");
        }
        if (expiredAt == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료 시각은 필수입니다.");
        }
    }

    public IssuedCoupon markUsed() {
        if (status != CouponStatus.AVAILABLE) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용 가능한 쿠폰만 사용할 수 있습니다.");
        }
        return new IssuedCoupon(memberId, couponId, CouponStatus.USED, issuedAt, expiredAt, LocalDateTime.now());
    }

    public IssuedCoupon markAvailable() {
        return new IssuedCoupon(memberId, couponId, CouponStatus.AVAILABLE, issuedAt, expiredAt, null);
    }

    public void validateOwner(String requesterMemberId) {
        if (requesterMemberId == null || requesterMemberId.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "요청자 식별자는 필수입니다.");
        }
        if (!memberId.equals(requesterMemberId)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "요청자와 쿠폰 소유자가 일치하지 않습니다.");
        }
    }

    public void validateUsable(LocalDateTime now) {
        if (now == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "기준 시각은 필수입니다.");
        }
        if (status != CouponStatus.AVAILABLE) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용 가능한 쿠폰이 아닙니다.");
        }
        if (now.isAfter(expiredAt)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰입니다.");
        }
    }

    public CouponStatus resolveStatusAt(LocalDateTime now) {
        if (now == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "기준 시각은 필수입니다.");
        }
        if (status == CouponStatus.USED) {
            return CouponStatus.USED;
        }
        if (now.isAfter(expiredAt)) {
            return CouponStatus.EXPIRED;
        }
        return CouponStatus.AVAILABLE;
    }
}
