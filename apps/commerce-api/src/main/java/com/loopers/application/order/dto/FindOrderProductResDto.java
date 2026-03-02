package com.loopers.application.order.dto;

import com.loopers.domain.order.model.OrderProduct;

public record FindOrderProductResDto(
        Long productId,
        String productName,
        int price,
        int quantity
) {
    public static FindOrderProductResDto from(OrderProduct orderProduct) {
        return new FindOrderProductResDto(
                orderProduct.getProductId(),
                orderProduct.getProductName().value(),
                orderProduct.getPrice().value(),
                orderProduct.getQuantity().value()
        );
    }
}
