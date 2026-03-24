package com.loopers.domain.order.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderCreatedEvent(String orderId, Long memberId,
                                BigDecimal totalAmount, LocalDateTime createdAt) {
}
