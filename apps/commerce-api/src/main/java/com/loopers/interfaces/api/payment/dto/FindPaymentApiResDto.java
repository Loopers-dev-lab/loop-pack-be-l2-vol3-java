package com.loopers.interfaces.api.payment.dto;

import com.loopers.application.payment.dto.FindPaymentResDto;
import com.loopers.domain.payment.PaymentStatus;

public record FindPaymentApiResDto(
        Long id,
        String orderId,
        String transactionKey,
        String cardType,
        String cardNo,
        String amount,
        PaymentStatus status,
        String failReason
) {
    public static FindPaymentApiResDto from(FindPaymentResDto dto) {
        return new FindPaymentApiResDto(
                dto.id(),
                dto.orderId(),
                dto.transactionKey(),
                dto.cardType(),
                dto.cardNo(),
                dto.amount(),
                dto.status(),
                dto.failReason()
        );
    }
}
