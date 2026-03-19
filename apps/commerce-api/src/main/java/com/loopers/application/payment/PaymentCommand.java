package com.loopers.application.payment;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.gateway.PgType;

public record PaymentCommand() {

    public record Request(Long orderId, CardType cardType, String cardNo, PgType pgType) {

        public static Request of(Long orderId, CardType cardType, String cardNo, PgType pgType) {
            return new Request(orderId, cardType, cardNo, pgType);
        }
    }

    public record Cancel(String cancelReason, Long cancelAmount) {

        public static Cancel of(String cancelReason, Long cancelAmount) {
            return new Cancel(cancelReason, cancelAmount);
        }
    }
}
