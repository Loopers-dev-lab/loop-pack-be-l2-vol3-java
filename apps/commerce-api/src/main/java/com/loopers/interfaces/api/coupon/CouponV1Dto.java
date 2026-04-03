package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponIssueResultInfo;
import com.loopers.application.coupon.UserCouponInfo;
import com.loopers.domain.coupon.CouponIssueStatus;
import com.loopers.domain.coupon.CouponStatus;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;

public class CouponV1Dto {

    /**
     * 발급 쿠폰 응답 (발급/목록 조회 공용)
     */
    public record UserCouponResponse(
            Long userCouponId,
            Long couponTemplateId,
            CouponStatus status,
            LocalDateTime expiredAt,
            ZonedDateTime issuedAt,
            LocalDateTime usedAt
    ) {
        public static UserCouponResponse from(UserCouponInfo info) {
            return new UserCouponResponse(
                    info.id(),
                    info.couponTemplateId(),
                    info.status(),
                    info.expiredAt(),
                    info.issuedAt(),
                    info.usedAt()
            );
        }
    }

    /**
     * 내 쿠폰 목록 응답
     */
    public record MyCouponListResponse(
            java.util.List<UserCouponResponse> coupons
    ) {
        public static MyCouponListResponse from(java.util.List<UserCouponInfo> infos) {
            return new MyCouponListResponse(
                    infos.stream().map(UserCouponResponse::from).toList()
            );
        }
    }

    /**
     * 선착순 쿠폰 발급 요청 결과 응답 (발급 요청 + polling 공용)
     */
    public record CouponIssueResultResponse(
            Long issueResultId,
            Long couponTemplateId,
            CouponIssueStatus status,
            String rejectReason,
            ZonedDateTime requestedAt
    ) {
        public static CouponIssueResultResponse from(CouponIssueResultInfo info) {
            return new CouponIssueResultResponse(
                    info.id(),
                    info.couponTemplateId(),
                    info.status(),
                    info.rejectReason(),
                    info.createdAt()
            );
        }
    }
}
