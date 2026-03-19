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

    /** PG 콜백 Body (06 §3). amount 있으면 주문 금액과 대조 (06 §11.7). */
    public record PaymentCallbackRequest(
            String paymentId,
            @NotNull(message = "orderId는 필수입니다.")
            Long orderId,
            @NotNull(message = "success는 필수입니다.")
            Boolean success,
            String failureReason,
            Long amount
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
