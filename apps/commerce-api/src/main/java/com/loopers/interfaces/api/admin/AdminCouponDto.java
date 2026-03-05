package com.loopers.interfaces.api.admin;

import com.loopers.application.coupon.CouponInfo;
import com.loopers.application.coupon.IssuedCouponInfo;
import com.loopers.domain.common.Money;
import com.loopers.domain.coupon.DiscountType;
import com.loopers.domain.coupon.IssuedCouponStatus;

import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

public class AdminCouponDto {

    public record CreateRequest(
            String name,
            DiscountType discountType,
            BigDecimal discountValue,
            BigDecimal minOrderAmount,
            BigDecimal maxDiscountAmount,
            int totalQuantity,
            ZonedDateTime validFrom,
            ZonedDateTime validUntil
    ) {
        public Money toDiscountValue() { return Money.of(discountValue); }
        public Money toMinOrderAmount() { return minOrderAmount != null ? Money.of(minOrderAmount) : Money.zero(); }
        public Money toMaxDiscountAmount() { return maxDiscountAmount != null ? Money.of(maxDiscountAmount) : null; }
    }

    public record UpdateRequest(
            String name,
            DiscountType discountType,
            BigDecimal discountValue,
            BigDecimal minOrderAmount,
            BigDecimal maxDiscountAmount,
            int totalQuantity,
            ZonedDateTime validFrom,
            ZonedDateTime validUntil
    ) {
        public Money toDiscountValue() { return Money.of(discountValue); }
        public Money toMinOrderAmount() { return minOrderAmount != null ? Money.of(minOrderAmount) : Money.zero(); }
        public Money toMaxDiscountAmount() { return maxDiscountAmount != null ? Money.of(maxDiscountAmount) : null; }
    }

    public record CouponResponse(
            Long couponId,
            String name,
            DiscountType discountType,
            BigDecimal discountValue,
            BigDecimal minOrderAmount,
            BigDecimal maxDiscountAmount,
            int totalQuantity,
            int issuedQuantity,
            ZonedDateTime validFrom,
            ZonedDateTime validUntil,
            boolean deleted
    ) {
        public static CouponResponse from(CouponInfo info) {
            return new CouponResponse(
                    info.getCouponId(),
                    info.getName(),
                    info.getDiscountType(),
                    info.getDiscountValue().getAmount(),
                    info.getMinOrderAmount().getAmount(),
                    info.getMaxDiscountAmount() != null ? info.getMaxDiscountAmount().getAmount() : null,
                    info.getTotalQuantity(),
                    info.getIssuedQuantity(),
                    info.getValidFrom(),
                    info.getValidUntil(),
                    info.isDeleted()
            );
        }
    }

    public record CouponListResponse(
            List<CouponResponse> coupons,
            long totalElements,
            int totalPages,
            int page,
            int size
    ) {
        public static CouponListResponse from(Page<CouponInfo> infoPage) {
            return new CouponListResponse(
                    infoPage.getContent().stream().map(CouponResponse::from).toList(),
                    infoPage.getTotalElements(),
                    infoPage.getTotalPages(),
                    infoPage.getNumber(),
                    infoPage.getSize()
            );
        }
    }

    public record IssuedCouponResponse(
            Long issuedCouponId,
            Long couponId,
            Long userId,
            IssuedCouponStatus status,
            ZonedDateTime issuedAt,
            ZonedDateTime expiredAt,
            ZonedDateTime usedAt
    ) {
        public static IssuedCouponResponse from(IssuedCouponInfo info) {
            return new IssuedCouponResponse(
                    info.getIssuedCouponId(),
                    info.getCouponId(),
                    info.getUserId(),
                    info.getStatus(),
                    info.getIssuedAt(),
                    info.getExpiredAt(),
                    info.getUsedAt()
            );
        }
    }

    public record IssuedCouponListResponse(
            List<IssuedCouponResponse> issuedCoupons,
            long totalElements,
            int totalPages,
            int page,
            int size
    ) {
        public static IssuedCouponListResponse from(Page<IssuedCouponInfo> infoPage) {
            return new IssuedCouponListResponse(
                    infoPage.getContent().stream().map(IssuedCouponResponse::from).toList(),
                    infoPage.getTotalElements(),
                    infoPage.getTotalPages(),
                    infoPage.getNumber(),
                    infoPage.getSize()
            );
        }
    }
}
