package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponInfo;
import com.loopers.application.coupon.CouponIssueRequestInfo;
import com.loopers.application.coupon.UserCouponInfo;
import com.loopers.domain.event.CouponIssueRequestStatus;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.UserCouponStatus;

import java.time.ZonedDateTime;

public class CouponV1Dto {

    public record CouponIssueRequestResponse(
        Long requestId,
        Long couponId,
        CouponIssueRequestStatus status,
        String failureReason,
        ZonedDateTime requestedAt,
        ZonedDateTime updatedAt
    ) {
        public static CouponIssueRequestResponse from(CouponIssueRequestInfo info) {
            return new CouponIssueRequestResponse(
                info.requestId(),
                info.couponId(),
                info.status(),
                info.failureReason(),
                info.requestedAt(),
                info.updatedAt()
            );
        }
    }

    public record UserCouponResponse(
        Long id,
        UserCouponStatus status,
        Long orderId,
        ZonedDateTime issuedAt,
        ZonedDateTime usedAt,
        CouponResponse coupon
    ) {
        public static UserCouponResponse from(UserCouponInfo info) {
            return new UserCouponResponse(
                info.id(),
                info.status(),
                info.orderId(),
                info.issuedAt(),
                info.usedAt(),
                CouponResponse.from(info.coupon())
            );
        }
    }

    public record CouponResponse(
        Long id,
        String name,
        CouponType type,
        Long value,
        Long minOrderAmount,
        ZonedDateTime expiredAt,
        Long issueLimit,
        Long issuedCount
    ) {
        public static CouponResponse from(CouponInfo info) {
            return new CouponResponse(
                info.id(),
                info.name(),
                info.type(),
                info.value(),
                info.minOrderAmount(),
                info.expiredAt(),
                info.issueLimit(),
                info.issuedCount()
            );
        }
    }
}
