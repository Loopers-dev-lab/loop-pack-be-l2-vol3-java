package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentCommand;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.gateway.PgType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record PaymentRequest() {

    // Command

    public record Request(
            @NotNull Long orderId,
            @NotNull CardType cardType,
            @NotBlank @Pattern(regexp = "\\d{4}-\\d{4}-\\d{4}-\\d{4}", message = "카드 번호는 xxxx-xxxx-xxxx-xxxx 형식이어야 합니다")
            String cardNo,
            @NotNull PgType pgType
    ) {

        public PaymentCommand.Request toCommand() {
            return PaymentCommand.Request.of(orderId, cardType, cardNo, pgType);
        }
    }

    public record Cancel(
            @NotBlank String cancelReason,
            Long cancelAmount
    ) {

        public PaymentCommand.Cancel toCommand() {
            return PaymentCommand.Cancel.of(cancelReason, cancelAmount);
        }
    }
}
