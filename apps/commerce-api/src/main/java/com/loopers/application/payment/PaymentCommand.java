package com.loopers.application.payment;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.gateway.PgType;

import java.math.BigDecimal;

public record PaymentCommand() {

    public record Request(Long orderId, CardType cardType, String cardNo, PgType pgType) {

        public static Request of(Long orderId, CardType cardType, String cardNo, PgType pgType) {
            return new Request(orderId, cardType, cardNo, pgType);
        }
    }

    public record Create(Long orderId, Long userId, PgType pgType, CardType cardType, String cardNo, BigDecimal amount) {

        public static Create of(Long orderId, Long userId, PgType pgType, CardType cardType, String cardNo, BigDecimal amount) {
            return new Create(orderId, userId, pgType, cardType, cardNo, amount);
        }
    }

    public record Cancel(String cancelReason, Long cancelAmount) {

        public static Cancel of(String cancelReason, Long cancelAmount) {
            return new Cancel(cancelReason, cancelAmount);
        }
    }
}
