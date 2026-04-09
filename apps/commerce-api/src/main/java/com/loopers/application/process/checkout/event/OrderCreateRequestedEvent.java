package com.loopers.application.process.checkout.event;

import com.loopers.domain.payment.CardType;

import java.util.UUID;

public record OrderCreateRequestedEvent(
        String memberId,
        UUID orderId,
        UUID couponId,
        long usedPointAmount,
        CardType cardType,
        String cardNo,
        int amount
) {
}
