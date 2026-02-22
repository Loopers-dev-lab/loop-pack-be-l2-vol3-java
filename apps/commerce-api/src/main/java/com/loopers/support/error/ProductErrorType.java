package com.loopers.support.error;

import org.springframework.http.HttpStatus;

public enum ProductErrorType implements ErrorType {
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 상품입니다."),
    ALREADY_DELETED(HttpStatus.CONFLICT, "이미 삭제된 상품입니다."),
    NOT_DISPLAYABLE(HttpStatus.BAD_REQUEST, "노출 불가능한 상품입니다."),
    INVALID_PRODUCT_NAME(HttpStatus.BAD_REQUEST, "상품명은 필수입니다.");

    private final HttpStatus status;
    private final String message;

    ProductErrorType(HttpStatus status, String message) {
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
