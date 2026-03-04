package com.loopers.domain.coupon.model;

import com.loopers.support.CouponEnums;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class UserCoupon {

    private Long id;
    private Long couponTemplateId;
    private Long memberId;
    private CouponEnums.Status status;
    private LocalDateTime usedAt;

    private UserCoupon(Long couponTemplateId, Long memberId, CouponEnums.Status status) {
        this.couponTemplateId = couponTemplateId;
        this.memberId = memberId;
        this.status = status;
    }

    public static UserCoupon issue(Long couponTemplateId, Long memberId) {
        return new UserCoupon(couponTemplateId, memberId, CouponEnums.Status.AVAILABLE);
    }

    public static UserCoupon reconstruct(Long id, Long couponTemplateId, Long memberId,
                                          String status, LocalDateTime usedAt) {
        UserCoupon userCoupon = new UserCoupon(
                couponTemplateId,
                memberId,
                CouponEnums.Status.valueOf(status)
        );
        userCoupon.id = id;
        userCoupon.usedAt = usedAt;
        return userCoupon;
    }

    public void use() {
        validateUsable();
        this.status = CouponEnums.Status.USED;
        this.usedAt = LocalDateTime.now();
    }

    public void validateOwnership(Long requestMemberId) {
        if (!this.memberId.equals(requestMemberId)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "본인 소유의 쿠폰만 사용할 수 있습니다.");
        }
    }

    public void validateUsable() {
        if (this.status == CouponEnums.Status.USED) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이미 사용된 쿠폰입니다.");
        }
        if (this.status == CouponEnums.Status.EXPIRED) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰입니다.");
        }
    }
}
