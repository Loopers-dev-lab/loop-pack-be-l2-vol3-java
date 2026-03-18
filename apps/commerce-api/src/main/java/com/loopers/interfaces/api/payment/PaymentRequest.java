package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentCommand;
import com.loopers.domain.payment.CardType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record PaymentRequest(
        @NotNull(message = "회원 ID는 필수입니다.")
        Long memberId,

        @NotNull(message = "주문 ID는 필수입니다.")
        Long orderId,

        @NotNull(message = "카드 종류는 필수입니다.")
        CardType cardType,

        @NotBlank(message = "카드 번호는 필수입니다.")
        String cardNo,

        @NotNull(message = "결제 금액은 필수입니다.")
        @Positive(message = "결제 금액은 0보다 커야 합니다.")
        BigDecimal amount
) {
    public PaymentCommand toCommand() {
        return new PaymentCommand(orderId, memberId, cardType, cardNo, amount);
    }
}
