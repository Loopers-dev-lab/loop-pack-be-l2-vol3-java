package com.loopers.interfaces.api.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.data.domain.Page;

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
        ZonedDateTime expiredAt,
        /** null이면 전역 발급 상한 없음 */
        Integer maxIssueCount
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
        ZonedDateTime expiredAt,
        /** null이면 전역 발급 상한 없음 */
        Integer maxIssueCount
    ) {}

    public record CouponResponse(
        Long id,
        String name,
        String type,
        int value,
        BigDecimal minOrderAmount,
        ZonedDateTime expiredAt,
        Integer maxIssueCount,
        int issuedCount
    ) {}

    public record IssuedCouponResponse(
        Long id,
        Long couponId,
        String status,
        ZonedDateTime expiredAt,
        ZonedDateTime usedAt,
        ZonedDateTime createdAt
    ) {}

    /** 페이지 결과 (Jackson 역직렬화를 위해 Page 대신 DTO 사용) */
    public record PagedCouponsResponse(
        java.util.List<CouponResponse> content,
        long totalElements,
        int totalPages,
        int number,
        int size
    ) {
        public static PagedCouponsResponse from(Page<CouponResponse> page) {
            if (page == null) {
                return new PagedCouponsResponse(List.of(), 0L, 0, 0, 0);
            }
            return new PagedCouponsResponse(
                page.getContent(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.getNumber(),
                page.getSize()
            );
        }
    }

    /** 발급 이력 페이지 결과 */
    public record PagedIssuedCouponsResponse(
        java.util.List<IssuedCouponResponse> content,
        long totalElements,
        int totalPages,
        int number,
        int size
    ) {
        public static PagedIssuedCouponsResponse from(Page<IssuedCouponResponse> page) {
            if (page == null) {
                return new PagedIssuedCouponsResponse(List.of(), 0L, 0, 0, 0);
            }
            return new PagedIssuedCouponsResponse(
                page.getContent(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.getNumber(),
                page.getSize()
            );
        }
    }
}
