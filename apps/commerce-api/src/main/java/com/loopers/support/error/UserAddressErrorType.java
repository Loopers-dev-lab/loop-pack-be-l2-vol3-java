package com.loopers.support.error;

import org.springframework.http.HttpStatus;

public enum UserAddressErrorType implements ErrorType {
    ADDRESS_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 배송지입니다."),
    NOT_OWNER(HttpStatus.FORBIDDEN, "본인의 배송지가 아닙니다.");

    private final HttpStatus status;
    private final String message;

    UserAddressErrorType(HttpStatus status, String message) {
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
