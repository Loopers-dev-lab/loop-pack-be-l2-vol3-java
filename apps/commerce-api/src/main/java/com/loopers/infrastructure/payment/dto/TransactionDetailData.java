package com.loopers.infrastructure.payment.dto;

public record TransactionDetailData(
            String transactionKey,
            String orderId,
            String cardType,
            String cardNo,
            long amount,
            String status,
            String reason) {
    }
