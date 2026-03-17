package com.loopers.application.payment;

import com.loopers.domain.payment.CardType;

public record PaymentCommand() {

    public record Request(Long orderId, CardType cardType, String cardNo) {

        public static Request of(Long orderId, CardType cardType, String cardNo) {
            return new Request(orderId, cardType, cardNo);
        }
    }

    public record Callback(String transactionKey, String status, String reason) {

        public static Callback of(String transactionKey, String status, String reason) {
            return new Callback(transactionKey, status, reason);
        }

        public boolean isSuccess() {
            return "SUCCESS".equals(status);
        }
    }
}
