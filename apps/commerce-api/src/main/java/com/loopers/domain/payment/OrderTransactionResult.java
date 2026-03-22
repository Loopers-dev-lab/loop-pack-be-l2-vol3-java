package com.loopers.domain.payment;

import java.util.List;

public record OrderTransactionResult(
        String orderId,
        List<TransactionResult> transactions
) {
}
