package com.loopers.interfaces.apiadmin;

import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.support.enums.DiscountType;
import com.loopers.support.enums.UserCouponStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 관리자 쿠폰 API V1 요청/응답 DTO 모음.
 */
public class AdminCouponV1Dto {

    /**
     * 쿠폰 생성/수정 요청 DTO.
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CouponRequest {
        @NotBlank(message = "쿠폰명은 필수입니다")
        private String name;

        @NotNull(message = "할인 유형은 필수입니다")
        private DiscountType type;

        @NotNull(message = "할인값은 필수입니다")
        @DecimalMin(value = "0.01", message = "할인값은 0보다 커야 합니다")
        private BigDecimal value;

        private BigDecimal minOrderAmount;

        @NotNull(message = "만료 일시는 필수입니다")
        private LocalDateTime expiredAt;

        private Integer maxQuantity;
    }

    /**
     * 쿠폰 응답 DTO.
     */
    @Getter
    @AllArgsConstructor
    @Builder
    public static class CouponResponse {
        private Long couponId;
        private String name;
        private DiscountType discountType;
        private BigDecimal discountValue;
        private BigDecimal minOrderAmount;
        private LocalDateTime expiredAt;
        private Integer maxQuantity;
        private int issuedCount;
        private String delYn;
        private LocalDateTime deletedAt;

        public static CouponResponse from(CouponModel coupon) {
            return CouponResponse.builder()
                    .couponId(coupon.getCouponId())
                    .name(coupon.getName())
                    .discountType(coupon.getDiscountType())
                    .discountValue(coupon.getDiscountValue())
                    .minOrderAmount(coupon.getMinOrderAmount())
                    .expiredAt(coupon.getExpiredAt())
                    .maxQuantity(coupon.getMaxQuantity())
                    .issuedCount(coupon.getIssuedCount())
                    .delYn(coupon.getDelYn())
                    .deletedAt(coupon.getDeletedAt() != null ? coupon.getDeletedAt().toLocalDateTime() : null)
                    .build();
        }
    }

    /**
     * 발급 내역 응답 DTO.
     */
    @Getter
    @AllArgsConstructor
    @Builder
    public static class IssueHistoryResponse {
        private Long userCouponId;
        private Long userId;
        private Long couponId;
        private UserCouponStatus status;
        private LocalDateTime issuedAt;
        private LocalDateTime usedAt;
        private Long orderId;

        public static IssueHistoryResponse from(UserCouponModel userCoupon) {
            return IssueHistoryResponse.builder()
                    .userCouponId(userCoupon.getUserCouponId())
                    .userId(userCoupon.getUserId())
                    .couponId(userCoupon.getCouponId())
                    .status(userCoupon.getStatus())
                    .issuedAt(userCoupon.getIssuedAt())
                    .usedAt(userCoupon.getUsedAt())
                    .orderId(userCoupon.getOrderId())
                    .build();
        }
    }
}
