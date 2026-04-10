package com.loopers.interfaces.datagenerator.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

public class AdminDataGeneratorV1Dto {

    public record GenerateLikesRequest(
        @NotNull(message = "상품 ID 목록은 필수입니다.")
        List<Long> productIds,
        @NotNull(message = "상품당 좋아요 수는 필수입니다.")
        @Min(value = 1, message = "상품당 좋아요 수는 1 이상이어야 합니다.")
        Integer likesPerProduct
    ) {}

    public record GenerateLikesResponse(
        int totalCreated,
        int skipped,
        String message
    ) {}

    public record StatsResponse(
        long brandCount,
        long productCount,
        long likeCount,
        long userCount,
        long orderCount,
        long couponCount,
        long ownedCouponCount,
        Map<String, Object> details
    ) {}

    // === User Generation ===

    public record GenerateUsersRequest(
        @NotBlank(message = "접두사는 필수입니다.")
        String prefix,
        @NotNull @Min(value = 1, message = "생성 수는 1 이상이어야 합니다.")
        Integer count,
        @Min(value = 0, message = "기본 포인트는 0 이상이어야 합니다.")
        Long defaultPoint
    ) {}

    public record GenerateUsersResponse(
        int totalCreated,
        String message
    ) {}

    // === Order Generation ===

    public record GenerateOrdersRequest(
        @NotNull(message = "유저 ID 목록은 필수입니다.")
        List<Long> userIds,
        String mode,
        List<OrderItemSpec> items,
        Integer itemsPerOrder
    ) {
        public record OrderItemSpec(
            @NotNull Long productId,
            @Min(1) int quantity
        ) {}
    }

    public record GenerateOrdersResponse(
        int totalCreated,
        int totalFailed,
        String message
    ) {}

    // === Coupon Generation ===

    public record GenerateCouponsRequest(
        @NotNull @Min(value = 1, message = "쿠폰 수는 1 이상이어야 합니다.")
        Integer count,
        @NotBlank(message = "할인 타입은 필수입니다.")
        String discountType,
        @NotNull @Min(value = 1, message = "할인 값은 1 이상이어야 합니다.")
        Long discountValue,
        Long minOrderAmount,
        @NotNull @Min(value = 1, message = "총 수량은 1 이상이어야 합니다.")
        Integer totalQuantityPerCoupon,
        boolean issueToAllUsers
    ) {}

    public record GenerateCouponsResponse(
        int couponsCreated,
        int totalIssued,
        String message
    ) {}

    // === Queue Bulk Enter ===

    public record BulkQueueEnterRequest(
        String prefix,
        Integer count
    ) {}
}
