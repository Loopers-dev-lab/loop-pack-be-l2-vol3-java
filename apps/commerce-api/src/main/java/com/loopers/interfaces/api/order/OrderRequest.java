package com.loopers.interfaces.api.order;

import java.util.List;

public class OrderRequest {

    public record CreateOrderRequest(
            List<OrderItemRequest> items,
            Long addressId
    ) {}

    public record OrderItemRequest(
            Long productId,
            int quantity
    ) {}
}
