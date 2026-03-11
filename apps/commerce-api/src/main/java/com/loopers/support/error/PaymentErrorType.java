package com.loopers.support.error;

import org.springframework.http.HttpStatus;

public enum PaymentErrorType implements ErrorType {
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 결제입니다."),
    DUPLICATE_IDEMPOTENCY_KEY(HttpStatus.CONFLICT, "이미 처리된 결제 요청입니다."),
    INVALID_PAYMENT_STATUS(HttpStatus.CONFLICT, "현재 결제 상태에서는 수행할 수 없는 작업입니다.");

    private final HttpStatus status;
    private final String message;

    PaymentErrorType(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    @Override
    public HttpStatus getStatus() {
        return this.status;
    }

    @Override
    public String getCode() {
        return name();
    }

    @Override
    public String getMessage() {
        return this.message;
    }
}
