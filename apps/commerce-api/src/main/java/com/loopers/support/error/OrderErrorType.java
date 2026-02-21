package com.loopers.support.error;

import org.springframework.http.HttpStatus;

public enum OrderErrorType implements ErrorType {
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 주문입니다."),
    NOT_OWNER(HttpStatus.FORBIDDEN, "본인의 주문이 아닙니다."),
    INVALID_ORDER_STATUS(HttpStatus.CONFLICT, "현재 주문 상태에서는 수행할 수 없는 작업입니다.");

    private final HttpStatus status;
    private final String message;

    OrderErrorType(HttpStatus status, String message) {
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
