package com.loopers.domain.event;

import java.util.List;

public record OrderCreatedEvent(long orderId, long memberId, List<OrderItemSnapshot> items) {
}
