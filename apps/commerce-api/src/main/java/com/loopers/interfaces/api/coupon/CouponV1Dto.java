package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponDto;
import com.loopers.domain.coupon.CouponIssueRequestStatus;
import com.loopers.domain.coupon.CouponIssueStatus;
import com.loopers.domain.coupon.CouponType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

public class CouponV1Dto {

    // ─────────────────────────────────────────
    // Admin: 쿠폰 템플릿 등록 / 수정
    // ─────────────────────────────────────────

    public record RegisterTemplateRequest(
        @NotBlank(message = "쿠폰 이름은 필수입니다.")
        String name,

        String description,

        @NotNull(message = "쿠폰 타입은 필수입니다.")
        CouponType type,

        @NotNull(message = "할인 값은 필수입니다.")
        @DecimalMin(value = "0.01", message = "할인 값은 0보다 커야 합니다.")
        BigDecimal discountValue,

        @NotNull(message = "만료일은 필수입니다.")
        ZonedDateTime expiredAt
    ) {
    }

    public record UpdateTemplateRequest(
        @NotBlank(message = "쿠폰 이름은 필수입니다.")
        String name,

        String description,

        @NotNull(message = "쿠폰 타입은 필수입니다.")
        CouponType type,

        @NotNull(message = "할인 값은 필수입니다.")
        @DecimalMin(value = "0.01", message = "할인 값은 0보다 커야 합니다.")
        BigDecimal discountValue,

        @NotNull(message = "만료일은 필수입니다.")
        ZonedDateTime expiredAt
    ) {
    }

    // ─────────────────────────────────────────
    // Responses
    // ─────────────────────────────────────────

    public record CouponTemplateResponse(
        Long id,
        String name,
        String description,
        CouponType type,
        BigDecimal discountValue,
        ZonedDateTime expiredAt,
        ZonedDateTime createdAt
    ) {
        public static CouponTemplateResponse from(CouponDto.CouponInfo info) {
            return new CouponTemplateResponse(
                info.id(),
                info.name(),
                info.description(),
                info.type(),
                info.discountValue(),
                info.expiredAt(),
                info.createdAt()
            );
        }
    }

    public record CouponTemplatePageResponse(
        List<CouponTemplateResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
    ) {
        public static CouponTemplatePageResponse from(Page<CouponDto.CouponInfo> page) {
            return new CouponTemplatePageResponse(
                page.getContent().stream().map(CouponTemplateResponse::from).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
            );
        }
    }

    public record CouponIssueResponse(
        Long id,
        Long couponId,
        Long userId,
        CouponIssueStatus status,
        ZonedDateTime expiredAt,
        ZonedDateTime usedAt,
        ZonedDateTime createdAt
    ) {
        public static CouponIssueResponse from(CouponDto.CouponIssueInfo info) {
            return new CouponIssueResponse(
                info.id(),
                info.couponId(),
                info.userId(),
                info.status(),
                info.expiredAt(),
                info.usedAt(),
                info.createdAt()
            );
        }
    }

    public record CouponIssuePageResponse(
        List<CouponIssueResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
    ) {
        public static CouponIssuePageResponse from(Page<CouponDto.CouponIssueInfo> page) {
            return new CouponIssuePageResponse(
                page.getContent().stream().map(CouponIssueResponse::from).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
            );
        }
    }

    public record MyCouponResponse(
        Long id,
        Long couponId,
        String couponName,
        CouponType couponType,
        BigDecimal discountValue,
        CouponDisplayStatus status,
        ZonedDateTime expiredAt,
        ZonedDateTime usedAt
    ) {
        public static MyCouponResponse from(CouponDto.MyCouponInfo info) {
            CouponDisplayStatus displayStatus = info.status() == CouponIssueStatus.USED
                ? CouponDisplayStatus.USED
                : info.expiredAt().isBefore(java.time.ZonedDateTime.now())
                    ? CouponDisplayStatus.EXPIRED
                    : CouponDisplayStatus.AVAILABLE;

            return new MyCouponResponse(
                info.id(),
                info.couponId(),
                info.couponName(),
                info.couponType(),
                info.discountValue(),
                displayStatus,
                info.expiredAt(),
                info.usedAt()
            );
        }
    }

    public record MyCouponPageResponse(
        List<MyCouponResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
    ) {
        public static MyCouponPageResponse from(Page<CouponDto.MyCouponInfo> page) {
            return new MyCouponPageResponse(
                page.getContent().stream().map(MyCouponResponse::from).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
            );
        }
    }

    public record CouponIssueRequestResponse(
        Long requestId,
        Long couponId,
        Long userId,
        CouponIssueRequestStatus status,
        String failReason
    ) {
        public static CouponIssueRequestResponse from(CouponDto.CouponIssueRequestInfo info) {
            return new CouponIssueRequestResponse(
                info.id(),
                info.couponId(),
                info.userId(),
                info.status(),
                info.failReason()
            );
        }
    }
}
