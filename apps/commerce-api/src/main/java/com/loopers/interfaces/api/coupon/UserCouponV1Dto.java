package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponIssueRequestInfo;
import com.loopers.application.coupon.UserCouponInfo;
import java.time.ZonedDateTime;
import java.util.List;
import org.springframework.data.domain.Page;

public class UserCouponV1Dto {

    public record IssueCouponResponse(
        Long userCouponId,
        Long couponId,
        String couponName,
        String status,
        ZonedDateTime issuedAt
    ) {
        public static IssueCouponResponse from(UserCouponInfo info) {
            return new IssueCouponResponse(
                info.userCouponId(),
                info.couponId(),
                info.couponName(),
                info.status(),
                info.issuedAt()
            );
        }
    }

    public record MyCouponResponse(
        Long userCouponId,
        String couponName,
        String status,
        ZonedDateTime issuedAt,
        ZonedDateTime usedAt
    ) {
        public static MyCouponResponse from(UserCouponInfo info) {
            return new MyCouponResponse(
                info.userCouponId(),
                info.couponName(),
                info.status(),
                info.issuedAt(),
                info.usedAt()
            );
        }
    }

    public record IssueAsyncResponse(
        String requestId,
        Long couponId,
        String status
    ) {
        public static IssueAsyncResponse from(CouponIssueRequestInfo info) {
            return new IssueAsyncResponse(info.requestId(), info.couponId(), info.status());
        }
    }

    public record IssueRequestStatusResponse(
        String requestId,
        Long couponId,
        String status,
        String failureReason
    ) {
        public static IssueRequestStatusResponse from(CouponIssueRequestInfo info) {
            return new IssueRequestStatusResponse(
                info.requestId(), info.couponId(), info.status(), info.failureReason()
            );
        }
    }

    public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
    ) {
        public static <T> PageResponse<T> from(Page<T> page) {
            return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
            );
        }
    }
}
