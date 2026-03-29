package com.loopers.application.coupon;

import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponIssue;
import com.loopers.domain.coupon.CouponIssueRequest;
import com.loopers.domain.coupon.CouponIssueRequestStatus;
import com.loopers.domain.coupon.CouponIssueStatus;
import com.loopers.domain.coupon.CouponType;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

public class CouponDto {

    public record CouponInfo(
        Long id,
        String name,
        String description,
        CouponType type,
        BigDecimal discountValue,
        ZonedDateTime expiredAt,
        ZonedDateTime createdAt
    ) {
        public static CouponInfo from(Coupon coupon) {
            return new CouponInfo(
                coupon.getId(),
                coupon.getName(),
                coupon.getDescription(),
                coupon.getType(),
                coupon.getDiscountValue(),
                coupon.getExpiredAt(),
                coupon.getCreatedAt()
            );
        }
    }

    public record CouponIssueInfo(
        Long id,
        Long couponId,
        Long userId,
        CouponIssueStatus status,
        ZonedDateTime expiredAt,
        ZonedDateTime usedAt,
        ZonedDateTime createdAt
    ) {
        public static CouponIssueInfo from(CouponIssue issue) {
            return new CouponIssueInfo(
                issue.getId(),
                issue.getCouponId(),
                issue.getUserId(),
                issue.getStatus(),
                issue.getExpiredAt(),
                issue.getUsedAt(),
                issue.getCreatedAt()
            );
        }
    }

    public record MyCouponInfo(
        Long id,
        Long couponId,
        String couponName,
        CouponType couponType,
        BigDecimal discountValue,
        CouponIssueStatus status,
        ZonedDateTime expiredAt,
        ZonedDateTime usedAt
    ) {
        public static MyCouponInfo from(CouponIssue issue, Coupon coupon) {
            return new MyCouponInfo(
                issue.getId(),
                coupon.getId(),
                coupon.getName(),
                coupon.getType(),
                coupon.getDiscountValue(),
                issue.getStatus(),
                issue.getExpiredAt(),
                issue.getUsedAt()
            );
        }
    }

    public record CouponIssueRequestInfo(
        Long id,
        Long couponId,
        Long userId,
        CouponIssueRequestStatus status,
        String failReason
    ) {
        public static CouponIssueRequestInfo from(CouponIssueRequest request) {
            return new CouponIssueRequestInfo(
                request.getId(),
                request.getCouponId(),
                request.getUserId(),
                request.getStatus(),
                request.getFailReason()
            );
        }
    }
}
