package com.loopers.domain.event;

import java.util.List;

public record OrderCancelledEvent(long orderId, long memberId, List<OrderItemSnapshot> items) {
}
