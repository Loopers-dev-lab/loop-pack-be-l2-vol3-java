package com.loopers.application.payment;

public record PgCallbackCommand(
        String transactionKey,
        String status,
        String reason
) {}
