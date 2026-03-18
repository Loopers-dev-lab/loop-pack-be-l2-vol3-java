package com.loopers.domain.payment;

public record PaymentMethod(
        CardType cardType,
        String cardNo
) {
}
