package com.loopers.support.error;

import org.springframework.http.HttpStatus;

public enum CartItemErrorType implements ErrorType {
    CART_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 장바구니 항목입니다."),
    INVALID_QUANTITY(HttpStatus.BAD_REQUEST, "유효하지 않은 수량입니다."),
    NOT_OWNER(HttpStatus.FORBIDDEN, "본인의 장바구니가 아닙니다."),
    NOT_PURCHASABLE(HttpStatus.CONFLICT, "판매 불가능한 상품입니다.");

    private final HttpStatus status;
    private final String message;

    CartItemErrorType(HttpStatus status, String message) {
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
