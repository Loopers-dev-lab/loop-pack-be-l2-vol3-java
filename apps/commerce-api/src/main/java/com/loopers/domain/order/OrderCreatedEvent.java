package com.loopers.domain.order;

import com.loopers.domain.payment.CardType;

public record OrderCreatedEvent(
    Long orderId,
    Long memberId,
    long amount,
    CardType cardType,
    String cardNo,
    boolean updateDefaultCard
) {}