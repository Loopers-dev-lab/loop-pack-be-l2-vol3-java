package com.loopers.interfaces.coupon.dto;

import com.loopers.application.coupon.dto.CouponCriteria;
import com.loopers.application.coupon.dto.CouponResult;
import com.loopers.domain.coupon.CouponDiscountType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.ZonedDateTime;
import java.util.List;

public class AdminCouponV1Dto {

    public record RegisterRequest(
        @NotBlank(message = "쿠폰명은 필수 입력값입니다.")
        String name,
        @NotNull(message = "할인 타입은 필수 입력값입니다.")
        CouponDiscountType discountType,
        @NotNull(message = "할인값은 필수 입력값입니다.")
        @Min(value = 1, message = "할인값은 1 이상이어야 합니다.")
        Long discountValue,
        @Min(value = 0, message = "최소 주문 금액은 0 이상이어야 합니다.")
        Long minOrderAmount,
        @NotNull(message = "총 발급 수량은 필수 입력값입니다.")
        @Min(value = 1, message = "총 발급 수량은 1 이상이어야 합니다.")
        Integer totalQuantity,
        @NotNull(message = "유효기간은 필수 입력값입니다.")
        ZonedDateTime expiredAt
    ) {
        public CouponCriteria.Create toCriteria() {
            return new CouponCriteria.Create(
                    name, discountType, discountValue,
                    minOrderAmount, totalQuantity, expiredAt);
        }
    }

    public record UpdateRequest(
        @NotBlank(message = "쿠폰명은 필수 입력값입니다.")
        String name,
        @NotNull(message = "유효기간은 필수 입력값입니다.")
        ZonedDateTime expiredAt
    ) {
        public CouponCriteria.Update toCriteria() {
            return new CouponCriteria.Update(name, expiredAt);
        }
    }

    public record DetailResponse(
        Long id,
        String name,
        String discountType,
        long discountValue,
        Long minOrderAmount,
        int totalQuantity,
        long issuedQuantity,
        ZonedDateTime expiredAt,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt
    ) {
        public static DetailResponse from(CouponResult.Detail result) {
            return new DetailResponse(
                    result.id(), result.name(), result.discountType(),
                    result.discountValue(), result.minOrderAmount(),
                    result.totalQuantity(), result.issuedQuantity(),
                    result.expiredAt(), result.createdAt(), result.updatedAt());
        }
    }

    public record ListItem(
        Long id,
        String name,
        String discountType,
        long discountValue,
        int totalQuantity,
        long issuedQuantity,
        ZonedDateTime expiredAt,
        ZonedDateTime createdAt
    ) {
        public static ListItem from(CouponResult.Detail result) {
            return new ListItem(
                    result.id(), result.name(), result.discountType(),
                    result.discountValue(), result.totalQuantity(),
                    result.issuedQuantity(), result.expiredAt(), result.createdAt());
        }
    }

    public record ListResponse(
        int page,
        int size,
        long totalElements,
        int totalPages,
        List<ListItem> items
    ) {}

    public record IssueListItem(
        Long ownedCouponId,
        Long userId,
        String status,
        ZonedDateTime usedAt,
        ZonedDateTime issuedAt
    ) {
        public static IssueListItem from(CouponResult.IssuedDetail result) {
            return new IssueListItem(
                    result.id(), result.userId(), result.status(),
                    result.usedAt(), result.createdAt());
        }
    }

    public record IssueListResponse(
        int page,
        int size,
        long totalElements,
        int totalPages,
        List<IssueListItem> items
    ) {}
}
