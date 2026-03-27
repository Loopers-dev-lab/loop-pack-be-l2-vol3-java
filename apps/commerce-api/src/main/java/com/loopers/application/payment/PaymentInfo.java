package com.loopers.application.payment;

import com.loopers.domain.payment.PaymentModel;

// 레이어 간 데이터 전달용 DTO
// 도메인 엔티티(PaymentModel)를 직접 노출하지 않기 위해 사용
public record PaymentInfo(
        Long paymentId,
        String transactionId,
        String orderId,
        String status,
        int amount
) {
    public static PaymentInfo from(PaymentModel payment) {
        return new PaymentInfo(
                payment.getId(),
                payment.getTransactionId(),
                String.valueOf(payment.getOrderId()),
                payment.getStatus().name(),
                payment.getAmount()
        );
    }
}
