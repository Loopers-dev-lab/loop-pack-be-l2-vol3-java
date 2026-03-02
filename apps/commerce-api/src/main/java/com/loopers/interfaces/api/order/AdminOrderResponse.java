package com.loopers.interfaces.api.order;

import java.time.ZonedDateTime;
import java.util.List;

public class AdminOrderResponse {

    public record OrderSummary(
            Long orderId,
            String orderNumber,
            Long userId,
            String status,
            int totalAmount,
            ZonedDateTime createdAt
    ) {}

    public record OrderListResponse(
            List<OrderSummary> orders,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {}

    public record OrderItemDetail(
            String productName,
            String brandName,
            int unitPrice,
            int quantity,
            int lineTotal
    ) {}

    public record OrderDetail(
            Long orderId,
            String orderNumber,
            Long userId,
            String status,
            String ordererName,
            String ordererPhone,
            String receiverName,
            String receiverPhone,
            String zipCode,
            String addressLine1,
            String addressLine2,
            int subtotalAmount,
            int discountAmount,
            int pointUsedAmount,
            int shippingFee,
            int totalAmount,
            List<OrderItemDetail> items,
            ZonedDateTime createdAt
    ) {}
}
