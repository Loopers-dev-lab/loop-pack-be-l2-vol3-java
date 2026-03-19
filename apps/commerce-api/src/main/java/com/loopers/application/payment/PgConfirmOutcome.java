package com.loopers.application.payment;

public sealed interface PgConfirmOutcome {
    record Success() implements PgConfirmOutcome {}
    record Failed(String reason) implements PgConfirmOutcome {}
    record Timeout() implements PgConfirmOutcome {}
}
