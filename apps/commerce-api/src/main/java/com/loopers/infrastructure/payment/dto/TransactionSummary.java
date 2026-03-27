package com.loopers.infrastructure.payment.dto;

public record TransactionSummary(
            String transactionKey,
            String status,
            String reason) {
    }
