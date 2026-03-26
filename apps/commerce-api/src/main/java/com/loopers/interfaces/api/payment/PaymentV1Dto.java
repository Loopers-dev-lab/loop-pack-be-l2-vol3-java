package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentInfo;
import com.loopers.domain.payment.CardType;
import java.time.ZonedDateTime;

public class PaymentV1Dto {

    public record PayRequest(Long orderId, CardType cardType, String cardNo) {}

    public record CallbackRequest(String orderId, String transactionId, boolean success) {}

    public record PayResponse(Long paymentId, Long orderId, String status, long amount, ZonedDateTime createdAt) {
        public static PayResponse from(PaymentInfo info) {
            return new PayResponse(
                info.paymentId(),
                info.orderId(),
                info.status().name(),
                info.amount(),
                info.createdAt()
            );
        }
    }
}