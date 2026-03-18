package com.loopers.application.payment;

import com.loopers.domain.payment.CardType;

import java.math.BigDecimal;

public record PaymentCommand(Long orderId, Long memberId, CardType cardType, String cardNo, BigDecimal amount) {
}
