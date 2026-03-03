package com.loopers.application.order.query;
import java.util.UUID;

public record OrderAccessRequest(
        UUID orderId,
        UUID userId,
        boolean isAdmin
) {
}
