package com.loopers.interfaces.api.coupon;

import com.loopers.domain.PageResult;
import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponIssue;
import com.loopers.domain.coupon.CouponType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.ZonedDateTime;
import java.util.List;

public class AdminCouponV1Dto {

    public record CreateCouponRequest(
        @NotBlank(message = "쿠폰 이름은 필수입니다.")
        String name,

        @NotNull(message = "쿠폰 타입은 필수입니다.")
        CouponType type,

        @Min(value = 1, message = "쿠폰 값은 1 이상이어야 합니다.")
        int value,

        @Min(value = 0, message = "최소 주문 금액은 0 이상이어야 합니다.")
        int minOrderAmount,

        @NotNull(message = "만료일은 필수입니다.")
        ZonedDateTime expiredAt
    ) {}

    public record UpdateCouponRequest(
        @NotBlank(message = "쿠폰 이름은 필수입니다.")
        String name,

        @NotNull(message = "쿠폰 타입은 필수입니다.")
        CouponType type,

        @Min(value = 1, message = "쿠폰 값은 1 이상이어야 합니다.")
        int value,

        @Min(value = 0, message = "최소 주문 금액은 0 이상이어야 합니다.")
        int minOrderAmount,

        @NotNull(message = "만료일은 필수입니다.")
        ZonedDateTime expiredAt
    ) {}

    public record CouponResponse(
        Long id,
        String name,
        String type,
        int value,
        int minOrderAmount,
        ZonedDateTime expiredAt,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt
    ) {
        public static CouponResponse from(Coupon coupon) {
            return new CouponResponse(
                coupon.getId(), coupon.getName(), coupon.getType().name(),
                coupon.getValue(), coupon.getMinOrderAmount(), coupon.getExpiredAt(),
                coupon.getCreatedAt(), coupon.getUpdatedAt()
            );
        }
    }

    public record CouponPageResponse(
        List<CouponResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
    ) {
        public static CouponPageResponse from(PageResult<Coupon> result) {
            List<CouponResponse> content = result.items().stream()
                .map(CouponResponse::from)
                .toList();
            return new CouponPageResponse(content, result.page(), result.size(), result.totalElements(), result.totalPages());
        }
    }

    public record CouponIssueResponse(
        Long id,
        Long couponId,
        Long userId,
        String status,
        ZonedDateTime usedAt,
        ZonedDateTime createdAt
    ) {
        public static CouponIssueResponse from(CouponIssue issue) {
            return new CouponIssueResponse(
                issue.getId(), issue.getCouponId(), issue.getUserId(),
                issue.getStatus().name(), issue.getUsedAt(), issue.getCreatedAt()
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
        public static CouponIssuePageResponse from(PageResult<CouponIssue> result) {
            List<CouponIssueResponse> content = result.items().stream()
                .map(CouponIssueResponse::from)
                .toList();
            return new CouponIssuePageResponse(content, result.page(), result.size(), result.totalElements(), result.totalPages());
        }
    }
}
