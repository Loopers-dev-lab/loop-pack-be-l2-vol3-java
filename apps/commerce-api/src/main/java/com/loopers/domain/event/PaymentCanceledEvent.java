package com.loopers.domain.event;


import com.loopers.application.event.OrderItemSnapshot;

import java.util.List;

public record PaymentCanceledEvent(
        Long paymentId,
        Long orderId,
        Long userId,
        List<OrderItemSnapshot> items
) {
}
