package com.loopers.application.payment;

public sealed interface PgConfirmOutcome {
    record Success(Long pgAmount) implements PgConfirmOutcome {}
    record Failed(String reason) implements PgConfirmOutcome {}
    record Timeout() implements PgConfirmOutcome {}
    record Unavailable() implements PgConfirmOutcome {}
    record AmountMismatch(Long pgAmount) implements PgConfirmOutcome {}
}
