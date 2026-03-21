package com.loopers.application.order.event;

import com.loopers.domain.payment.CardType;

import java.util.UUID;

public record OrderPaymentRequestEvent(
        String memberId,
        UUID orderId,
        CardType cardType,
        String cardNo,
        int amount
) {
}
