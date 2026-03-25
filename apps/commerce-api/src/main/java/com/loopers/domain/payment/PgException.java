package com.loopers.domain.payment;

/**
 * PG 통신 예외 (Domain Layer — PaymentClient 포트 계약)
 *
 * CoreException과 별도 계층: CB는 PG 예외만 record하고, 비즈니스 예외(CoreException)는 ignore한다.
 */
public abstract class PgException extends RuntimeException {

    protected PgException(String message) {
        super(message);
    }

    protected PgException(String message, Throwable cause) {
        super(message, cause);
    }
}
