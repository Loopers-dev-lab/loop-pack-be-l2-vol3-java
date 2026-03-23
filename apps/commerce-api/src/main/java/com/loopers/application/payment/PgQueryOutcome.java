package com.loopers.application.payment;

public sealed interface PgQueryOutcome {
    record Confirmed(Long pgAmount) implements PgQueryOutcome {}
    record NotConfirmed() implements PgQueryOutcome {}
    record AmountMismatch(Long pgAmount) implements PgQueryOutcome {}
}
