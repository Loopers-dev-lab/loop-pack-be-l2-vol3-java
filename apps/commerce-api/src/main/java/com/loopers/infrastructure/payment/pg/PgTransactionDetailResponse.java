package com.loopers.infrastructure.payment.pg;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PgStatus;

public record PgTransactionDetailResponse(
    String transactionKey,
    String orderId,
    CardType cardType,
    String cardNo,
    Long amount,
    PgStatus status,
    String reason
) {
}
