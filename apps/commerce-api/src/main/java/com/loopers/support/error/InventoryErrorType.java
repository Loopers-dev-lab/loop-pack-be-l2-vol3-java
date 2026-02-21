package com.loopers.support.error;

import org.springframework.http.HttpStatus;

public enum InventoryErrorType implements ErrorType {
    INVENTORY_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 재고입니다."),
    INSUFFICIENT_STOCK(HttpStatus.CONFLICT, "가용 재고가 부족합니다."),
    INVALID_QUANTITY(HttpStatus.BAD_REQUEST, "유효하지 않은 수량입니다.");

    private final HttpStatus status;
    private final String message;

    InventoryErrorType(HttpStatus status, String message) {
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
