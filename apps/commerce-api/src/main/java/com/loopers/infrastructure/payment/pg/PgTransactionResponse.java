package com.loopers.infrastructure.payment.pg;

import com.loopers.domain.payment.PgStatus;

public record PgTransactionResponse(String transactionKey, PgStatus status) {
}
