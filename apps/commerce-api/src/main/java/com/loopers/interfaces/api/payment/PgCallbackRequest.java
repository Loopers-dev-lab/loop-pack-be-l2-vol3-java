package com.loopers.interfaces.api.payment;

import com.loopers.domain.payment.PgStatus;

import java.math.BigDecimal;

public record PgCallbackRequest(
        String transactionKey,
        PgStatus status,
        BigDecimal amount
) {
}
