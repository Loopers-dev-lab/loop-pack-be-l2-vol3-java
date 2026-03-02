package com.loopers.application.cart;

import com.loopers.domain.cart.CartItemModel;

import java.time.ZonedDateTime;

/**
 * 장바구니 항목 응답용 애플리케이션 DTO.
 * Controller 응답에 사용하며, interfaces DTO와 분리한다.
 */
public record CartInfo(
    Long id,
    Long userId,
    Long productId,
    Long optionId,
    int quantity,
    ZonedDateTime createdAt,
    ZonedDateTime updatedAt
) {
    public static CartInfo from(CartItemModel item) {
        if (item == null) {
            return null;
        }
        return new CartInfo(
            item.getId(),
            item.getUserId(),
            item.getProductId(),
            item.getOptionId(),
            item.getQuantity(),
            item.getCreatedAt(),
            item.getUpdatedAt()
        );
    }
}
