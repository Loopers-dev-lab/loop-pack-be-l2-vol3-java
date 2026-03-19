package com.loopers.application.payment;

import com.loopers.domain.payment.CardType;

public record PaymentCommand() {

    public record Request(Long orderId, CardType cardType, String cardNo) {

        public static Request of(Long orderId, CardType cardType, String cardNo) {
            return new Request(orderId, cardType, cardNo);
        }
    }

    public record Cancel(String cancelReason, Long cancelAmount) {

        public static Cancel of(String cancelReason, Long cancelAmount) {
            return new Cancel(cancelReason, cancelAmount);
        }
    }
}
