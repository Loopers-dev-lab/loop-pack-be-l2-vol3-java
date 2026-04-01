package com.loopers.interfaces.api.order;

import java.util.List;

public class OrderRequest {

    public record CreateOrderRequest(
            List<OrderItemRequest> items,
            List<Long> cartItemIds,
            Long addressId,
            String ordererPhone,
            Long issuedCouponId,
            int pointAmount,
            String paymentMethod,
            String cardNo
    ) {}

    public record OrderItemRequest(
            Long productId,
            int quantity
    ) {}
}
