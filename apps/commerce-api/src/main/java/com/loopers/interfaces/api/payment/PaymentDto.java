package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentInfo;
import com.loopers.domain.payment.PaymentStatus;

import java.math.BigDecimal;
import java.util.List;

public class PaymentDto {

    public record PaymentRequest(
            Long orderId,
            String cardType,
            String cardNo
    ) {
    }

    public record CallbackRequest(
            String orderId,
            String transactionKey,
            String status,
            String message
    ) {
        public Long parseOrderId() {
            if (orderId == null) {
                return null;
            }
            String numeric = orderId.replaceAll("[^0-9]", "");
            return Long.parseLong(numeric);
        }
    }

    public record PaymentResponse(
            Long paymentId,
            Long orderId,
            Long userId,
            String transactionId,
            String cardType,
            String cardNo,
            BigDecimal amount,
            PaymentStatus status,
            String pgResponseMessage
    ) {
        public static PaymentResponse from(PaymentInfo info) {
            return new PaymentResponse(
                    info.getPaymentId(),
                    info.getOrderId(),
                    info.getUserId(),
                    info.getTransactionId(),
                    info.getCardType(),
                    info.getCardNo(),
                    info.getAmount().getAmount(),
                    info.getStatus(),
                    info.getPgResponseMessage()
            );
        }
    }

    public record PaymentListResponse(List<PaymentResponse> payments) {
        public static PaymentListResponse from(List<PaymentInfo> infoList) {
            return new PaymentListResponse(
                    infoList.stream().map(PaymentResponse::from).toList()
            );
        }
    }
}
