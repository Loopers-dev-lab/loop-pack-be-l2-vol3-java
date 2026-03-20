package com.loopers.application.payment;

import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentStatus;

/**
 * 결제 응답용 애플리케이션 DTO (06 §10.2).
 */
public record PaymentInfo(
        Long paymentId,
        Long orderId,
        String status,
        String pgTransactionId
) {
    public static PaymentInfo from(PaymentModel payment) {
        if (payment == null) {
            return null;
        }
        return new PaymentInfo(
                payment.getId(),
                payment.getOrderId(),
                payment.getStatus().name(),
                payment.getPgTransactionId()
        );
    }

    public static PaymentInfo pending(Long paymentId, Long orderId) {
        return new PaymentInfo(paymentId, orderId, PaymentStatus.PENDING.name(), null);
    }
}
