package com.loopers.interfaces.api.coupon;

import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponIssue;
import com.loopers.domain.coupon.CouponIssueStatus;

import java.time.ZonedDateTime;
import java.util.List;

public class CouponV1Dto {

    public record IssueCouponResponse(
        Long couponIssueId,
        String couponName,
        String couponType,
        int value,
        String status,
        ZonedDateTime issuedAt
    ) {
        public static IssueCouponResponse from(CouponIssue issue, Coupon coupon) {
            return new IssueCouponResponse(
                issue.getId(),
                coupon.getName(),
                coupon.getType().name(),
                coupon.getValue(),
                issue.getStatus().name(),
                issue.getCreatedAt()
            );
        }
    }

    public record MyCouponResponse(
        Long couponIssueId,
        String couponName,
        String couponType,
        int value,
        int minOrderAmount,
        String status,
        ZonedDateTime issuedAt,
        ZonedDateTime expiredAt
    ) {
        public static MyCouponResponse from(CouponIssue issue, Coupon coupon) {
            String effectiveStatus = resolveStatus(issue, coupon);
            return new MyCouponResponse(
                issue.getId(),
                coupon.getName(),
                coupon.getType().name(),
                coupon.getValue(),
                coupon.getMinOrderAmount(),
                effectiveStatus,
                issue.getCreatedAt(),
                coupon.getExpiredAt()
            );
        }

        private static String resolveStatus(CouponIssue issue, Coupon coupon) {
            if (issue.getStatus() == CouponIssueStatus.USED) {
                return "USED";
            }
            if (coupon.isExpired()) {
                return "EXPIRED";
            }
            return "AVAILABLE";
        }
    }

    public record MyCouponListResponse(List<MyCouponResponse> coupons) {
        public static MyCouponListResponse from(List<CouponIssue> issues, List<Coupon> coupons) {
            List<MyCouponResponse> responses = issues.stream()
                .map(issue -> {
                    Coupon coupon = coupons.stream()
                        .filter(c -> c.getId().equals(issue.getCouponId()))
                        .findFirst()
                        .orElse(null);
                    if (coupon == null) return null;
                    return MyCouponResponse.from(issue, coupon);
                })
                .filter(r -> r != null)
                .toList();
            return new MyCouponListResponse(responses);
        }
    }
}
