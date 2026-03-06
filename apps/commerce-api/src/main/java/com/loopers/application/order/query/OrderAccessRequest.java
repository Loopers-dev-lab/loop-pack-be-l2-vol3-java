package com.loopers.application.order.query;
import java.util.UUID;

public record OrderAccessRequest(
        UUID orderId,
        String memberId,
        boolean isAdmin
) {
    public OrderAccessRequest(UUID orderId, boolean isAdmin) {
        this(orderId, null, isAdmin);
    }
}
