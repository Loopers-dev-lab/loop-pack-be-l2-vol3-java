package com.loopers.interfaces.api.order;

import java.time.ZonedDateTime;
import java.util.List;

public class OrderResponse {

    public record OrderSummary(
            Long orderId,
            String orderNumber,
            String status,
            int totalAmount,
            ZonedDateTime createdAt
    ) {}

    public record OrderCursorListResponse(
            List<OrderSummary> orders,
            PagingInfo paging
    ) {}

    public record PagingInfo(
            boolean hasNext,
            String nextCursor,
            int size
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

    public record OrderCreateResponse(
            Long orderId,
            String orderNumber,
            String status,
            int totalAmount,
            Long paymentId
    ) {}
}
