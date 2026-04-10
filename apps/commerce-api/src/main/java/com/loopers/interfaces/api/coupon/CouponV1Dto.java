package com.loopers.interfaces.api.coupon;

import com.loopers.domain.coupon.CouponIssueResultModel;
import com.loopers.domain.coupon.CouponIssueStatus;
import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.support.enums.DiscountType;
import com.loopers.support.enums.UserCouponStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 쿠폰 고객 API V1 요청/응답 DTO 모음.
 */
public class CouponV1Dto {

    /**
     * 발급된 쿠폰 응답 DTO.
     */
    @Getter
    @AllArgsConstructor
    @Builder
    public static class UserCouponResponse {
        private Long userCouponId;
        private Long couponId;
        private String couponName;
        private DiscountType discountType;
        private BigDecimal discountValue;
        private BigDecimal minOrderAmount;
        private LocalDateTime expiredAt;
        private UserCouponStatus status;
        private LocalDateTime issuedAt;
        private LocalDateTime usedAt;

        /**
         * UserCouponModel과 CouponModel로부터 응답 DTO를 생성한다.
         */
        public static UserCouponResponse from(UserCouponModel userCoupon, CouponModel coupon) {
            return UserCouponResponse.builder()
                    .userCouponId(userCoupon.getUserCouponId())
                    .couponId(coupon.getCouponId())
                    .couponName(coupon.getName())
                    .discountType(coupon.getDiscountType())
                    .discountValue(coupon.getDiscountValue())
                    .minOrderAmount(coupon.getMinOrderAmount())
                    .expiredAt(coupon.getExpiredAt())
                    .status(userCoupon.getStatus())
                    .issuedAt(userCoupon.getIssuedAt())
                    .usedAt(userCoupon.getUsedAt())
                    .build();
        }
    }

    /**
     * 선착순 쿠폰 발급 요청 응답 DTO.
     */
    @Getter
    @AllArgsConstructor
    public static class RushIssueResponse {
        private String requestId;
    }

    /**
     * 선착순 쿠폰 발급 결과 응답 DTO.
     */
    @Getter
    @AllArgsConstructor
    @Builder
    public static class IssueResultResponse {
        private String requestId;
        private Long userId;
        private Long couponId;
        private CouponIssueStatus status;
        private String reason;
        private LocalDateTime createdAt;

        /**
         * CouponIssueResultModel로부터 응답 DTO를 생성한다.
         */
        public static IssueResultResponse from(CouponIssueResultModel model) {
            return IssueResultResponse.builder()
                    .requestId(model.getRequestId())
                    .userId(model.getUserId())
                    .couponId(model.getCouponId())
                    .status(model.getStatus())
                    .reason(model.getReason())
                    .createdAt(model.getCreatedAt())
                    .build();
        }
    }
}
