package com.loopers.interfaces.api.order.dto;

import com.loopers.application.order.OrderQueryUseCase;

import java.time.LocalDateTime;
import java.util.List;

public record OrderDetailResponse(
        Long id,
        String receiverName,
        String address,
        String deliveryRequest,
        String paymentMethod,
        int totalAmount,
        int discountAmount,
        int paymentAmount,
        String status,
        List<OrderItemResponse> items,
        LocalDateTime createdAt
) {
    public static OrderDetailResponse from(OrderQueryUseCase.OrderDetail detail) {
        List<OrderItemResponse> itemResponses = detail.items().stream()
                .map(item -> new OrderItemResponse(item.productId(), item.quantity(), item.unitPrice()))
                .toList();

        return new OrderDetailResponse(
                detail.id(),
                detail.receiverName(),
                detail.address(),
                detail.deliveryRequest(),
                detail.paymentMethod(),
                detail.totalAmount(),
                detail.discountAmount(),
                detail.paymentAmount(),
                detail.status(),
                itemResponses,
                detail.createdAt()
        );
    }

    public record OrderItemResponse(
            Long productId,
            int quantity,
            int unitPrice
    ) {}
}
