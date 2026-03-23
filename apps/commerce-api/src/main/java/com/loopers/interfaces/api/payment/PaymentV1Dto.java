package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentInfo;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.PaymentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class PaymentV1Dto {

    public record PaymentRequest(
        @NotBlank(message = "카드 종류는 필수입니다")
        String cardType,

        @NotBlank(message = "카드 번호는 필수입니다")
        @Pattern(regexp = "^\\d{4}-\\d{4}-\\d{4}-\\d{4}$", message = "카드 번호 형식이 올바르지 않습니다")
        String cardNo
    ) {}

    public record CallbackRequest(
        String transactionKey,
        String orderId,
        String cardType,
        String cardNo,
        Long amount,
        String status,
        String reason
    ) {}

    public record PaymentResponse(
        Long paymentId,
        Long orderId,
        PaymentStatus paymentStatus,
        OrderStatus orderStatus,
        String pgTransactionId,
        String failReason,
        Integer totalPrice
    ) {
        public static PaymentResponse from(PaymentInfo info) {
            return new PaymentResponse(
                info.paymentId(),
                info.orderId(),
                info.paymentStatus(),
                info.orderStatus(),
                info.pgTransactionId(),
                info.failReason(),
                info.totalPrice()
            );
        }
    }
}
