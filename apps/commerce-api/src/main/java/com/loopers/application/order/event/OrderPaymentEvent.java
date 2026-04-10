package com.loopers.application.order.event;

import com.loopers.domain.payment.CardType;

public record OrderPaymentEvent(
        Long orderId,
        Long userId,
        int totalPrice,
        CardType cardType,
        String cardNo) {
}
