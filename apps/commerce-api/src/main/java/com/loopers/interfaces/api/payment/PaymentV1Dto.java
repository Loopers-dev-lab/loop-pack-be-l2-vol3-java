package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentInfo;
import jakarta.validation.constraints.NotNull;

/**
 * 결제 API 요청/응답 DTO (06 §10.4).
 */
public class PaymentV1Dto {

    public record PaymentRequest(
            @NotNull(message = "주문 ID는 필수입니다.")
            Long orderId,
            @NotNull(message = "카드 타입은 필수입니다.")
            String cardType,
            @NotNull(message = "카드 번호는 필수입니다.")
            String cardNo
    ) {
    }

    public record PaymentResponse(
            Long paymentId,
            Long orderId,
            String status,
            String pgTransactionId
    ) {
        public static PaymentResponse from(PaymentInfo info) {
            if (info == null) {
                return null;
            }
            return new PaymentResponse(
                    info.paymentId(),
                    info.orderId(),
                    info.status(),
                    info.pgTransactionId()
            );
        }
    }
}
