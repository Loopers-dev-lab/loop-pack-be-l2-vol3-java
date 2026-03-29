package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.view.MyCouponView;
import com.loopers.application.coupon.view.CouponIssueRequestView;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class CouponDto {

    public record CouponIssueRequestResponse(
            UUID requestId,
            UUID couponId,
            String status,
            String failureReason,
            LocalDateTime requestedAt,
            LocalDateTime processedAt
    ) {
        public static CouponIssueRequestResponse from(CouponIssueRequestView view) {
            return new CouponIssueRequestResponse(
                    view.requestId(),
                    view.couponId(),
                    view.status().name(),
                    view.failureReason(),
                    view.requestedAt(),
                    view.processedAt()
            );
        }
    }

    public record MyCouponResponse(
            UUID couponId,
            String name,
            String type,
            int value,
            int minOrderAmount,
            LocalDateTime expiredAt,
            String status
    ) {
        public static MyCouponResponse from(MyCouponView view) {
            return new MyCouponResponse(
                    view.couponId(),
                    view.name(),
                    view.type().name(),
                    view.value(),
                    view.minOrderAmount(),
                    view.expiredAt(),
                    view.status().name()
            );
        }
    }

    public record MyCouponListResponse(
            List<MyCouponResponse> items
    ) {
        public static MyCouponListResponse from(List<MyCouponView> views) {
            return new MyCouponListResponse(views.stream().map(MyCouponResponse::from).toList());
        }
    }
}
