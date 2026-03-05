package com.loopers.application.order;

import java.util.List;

public record OrderCreateCommand(Long userId, List<OrderItemCommand> items, Long issuedCouponId) {
}
