package com.loopers.interfaces.api.cart;

import com.loopers.application.cart.CartInfo;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.ZonedDateTime;
import java.util.List;

public class CartV1Dto {

    public record AddItemRequest(
        @NotNull(message = "상품 ID는 필수입니다.")
        Long productId,
        Long optionId,
        @NotNull(message = "수량은 필수입니다.")
        @Min(value = 1, message = "수량은 1 이상이어야 합니다.")
        Integer quantity
    ) {
    }

    public record UpdateItemRequest(
        @NotNull(message = "수량은 필수입니다.")
        @Min(value = 1, message = "수량은 1 이상이어야 합니다.")
        Integer quantity,
        Long optionId
    ) {
    }

    public record RemoveItemsRequest(
        List<Long> cartItemIds
    ) {
    }

    public record CartItemResponse(
        Long id,
        Long userId,
        Long productId,
        Long optionId,
        int quantity,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt
    ) {
        public static CartItemResponse from(CartInfo info) {
            if (info == null) {
                return null;
            }
            return new CartItemResponse(
                info.id(),
                info.userId(),
                info.productId(),
                info.optionId(),
                info.quantity(),
                info.createdAt(),
                info.updatedAt()
            );
        }
    }
}
