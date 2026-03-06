package com.loopers.interfaces.api.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

/**
 * 어드민 쿠폰 템플릿 API 전용 DTO.
 */
public final class AdminCouponV1Dto {

    public record CreateCouponRequest(
        @NotBlank(message = "쿠폰 이름은 필수입니다.")
        String name,
        @NotBlank(message = "쿠폰 타입(FIXED|RATE)은 필수입니다.")
        String type,
        @NotNull(message = "value는 필수입니다.")
        Integer value,
        BigDecimal minOrderAmount,
        @NotNull(message = "만료일은 필수입니다.")
        ZonedDateTime expiredAt
    ) {}

    public record UpdateCouponRequest(
        @NotBlank(message = "쿠폰 이름은 필수입니다.")
        String name,
        @NotBlank(message = "쿠폰 타입(FIXED|RATE)은 필수입니다.")
        String type,
        @NotNull(message = "value는 필수입니다.")
        Integer value,
        BigDecimal minOrderAmount,
        @NotNull(message = "만료일은 필수입니다.")
        ZonedDateTime expiredAt
    ) {}

    public record CouponResponse(
        Long id,
        String name,
        String type,
        int value,
        BigDecimal minOrderAmount,
        ZonedDateTime expiredAt
    ) {}

    public record IssuedCouponResponse(
        Long id,
        Long couponId,
        String status,
        ZonedDateTime expiredAt,
        ZonedDateTime usedAt,
        ZonedDateTime createdAt
    ) {}
}
