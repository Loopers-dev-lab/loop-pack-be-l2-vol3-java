package com.loopers.application.order.query;

public record OrderAccessRequest(
        Long orderId,
        Long userId,
        boolean isAdmin
) {
}
