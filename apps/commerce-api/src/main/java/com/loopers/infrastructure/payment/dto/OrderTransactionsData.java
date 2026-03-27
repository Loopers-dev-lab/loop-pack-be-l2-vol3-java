package com.loopers.infrastructure.payment.dto;

import java.util.List;

public record OrderTransactionsData(
            String orderId,
            List<TransactionSummary> transactions) {
    }
