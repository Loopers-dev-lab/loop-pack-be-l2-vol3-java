package com.loopers.application.event;


import java.util.List;

public record PaymentCanceledEvent(
        Long paymentId,
        Long orderId,
        Long userId,
        List<OrderItemSnapshot> items
) {
}
