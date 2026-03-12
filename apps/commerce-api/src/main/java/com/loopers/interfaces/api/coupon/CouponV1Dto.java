package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponTemplateResult;
import com.loopers.application.coupon.CreateCouponRequest;
import com.loopers.application.coupon.IssuedCouponResult;
import com.loopers.domain.coupon.CouponDisplayStatus;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.IssuedCouponStatus;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * 쿠폰 API DTO.
 */
public class CouponV1Dto {

    private CouponV1Dto() {}

    // ===== 관리자 API =====

    /**
     * 쿠폰 생성 요청.
     */
    public record CreateCouponRequestDto(
        String name,
        CouponType type,
        Long value,
        Long minOrderAmount,
        Integer maxDiscountAmount,
        Integer maxIssueCount,
        ZonedDateTime expiredAt
    ) {
        public CreateCouponRequest toServiceRequest() {
            return new CreateCouponRequest(
                name, type, value, minOrderAmount, maxDiscountAmount, maxIssueCount, expiredAt
            );
        }
    }

    /**
     * 쿠폰 템플릿 응답.
     */
    public record CouponTemplateResponseDto(
        Long id,
        String name,
        CouponType type,
        long value,
        long minOrderAmount,
        Integer maxDiscountAmount,
        Integer maxIssueCount,
        int issuedCount,
        ZonedDateTime expiredAt,
        ZonedDateTime createdAt
    ) {
        public static CouponTemplateResponseDto from(CouponTemplateResult result) {
            return new CouponTemplateResponseDto(
                result.id(),
                result.name(),
                result.type(),
                result.value(),
                result.minOrderAmount(),
                result.maxDiscountAmount(),
                result.maxIssueCount(),
                result.issuedCount(),
                result.expiredAt(),
                result.createdAt()
            );
        }
    }

    /**
     * 쿠폰 템플릿 목록 응답.
     */
    public record CouponTemplateListResponseDto(
        List<CouponTemplateResponseDto> coupons,
        int page,
        int size,
        long totalElements,
        int totalPages
    ) {}

    /**
     * 발급된 쿠폰 목록 응답 (관리자용).
     */
    public record IssuedCouponListResponseDto(
        List<IssuedCouponSimpleResponseDto> issues,
        long totalCount
    ) {}

    public record IssuedCouponSimpleResponseDto(
        Long id,
        Long userId,
        IssuedCouponStatus status,
        CouponDisplayStatus displayStatus,
        boolean usable,
        ZonedDateTime usedAt,
        ZonedDateTime issuedAt,
        ZonedDateTime expiredAt
    ) {
        public static IssuedCouponSimpleResponseDto from(IssuedCouponResult result) {
            return new IssuedCouponSimpleResponseDto(
                result.id(),
                result.userId(),
                result.status(),
                result.displayStatus(),
                result.usable(),
                result.usedAt(),
                result.issuedAt(),
                result.expiredAt()
            );
        }
    }

    // ===== 사용자 API =====

    /**
     * 발급된 쿠폰 응답 (사용자용).
     */
    public record IssuedCouponResponseDto(
        Long id,
        Long couponTemplateId,
        String couponName,
        CouponType couponType,
        long couponValue,
        long minOrderAmount,
        Integer maxDiscountAmount,
        IssuedCouponStatus status,
        CouponDisplayStatus displayStatus,
        boolean usable,
        ZonedDateTime usedAt,
        ZonedDateTime issuedAt,
        ZonedDateTime expiredAt
    ) {
        public static IssuedCouponResponseDto from(IssuedCouponResult result) {
            return new IssuedCouponResponseDto(
                result.id(),
                result.couponTemplateId(),
                result.couponName(),
                result.couponType(),
                result.couponValue(),
                result.minOrderAmount(),
                result.maxDiscountAmount(),
                result.status(),
                result.displayStatus(),
                result.usable(),
                result.usedAt(),
                result.issuedAt(),
                result.expiredAt()
            );
        }
    }

    /**
     * 내 쿠폰 목록 응답.
     */
    public record MyCouponListResponseDto(
        List<IssuedCouponResponseDto> coupons,
        long totalCount
    ) {}
}
