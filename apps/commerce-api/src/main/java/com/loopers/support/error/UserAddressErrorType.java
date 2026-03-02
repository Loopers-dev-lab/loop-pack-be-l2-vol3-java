package com.loopers.support.error;

import org.springframework.http.HttpStatus;

public enum UserAddressErrorType implements ErrorType {
    ADDRESS_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 배송지입니다."),
    NOT_OWNER(HttpStatus.FORBIDDEN, "본인의 배송지가 아닙니다."),
    INVALID_RECEIVER_NAME(HttpStatus.BAD_REQUEST, "수령인명은 필수입니다."),
    INVALID_PHONE(HttpStatus.BAD_REQUEST, "전화번호는 필수입니다."),
    INVALID_ADDRESS(HttpStatus.BAD_REQUEST, "주소 정보가 유효하지 않습니다.");

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
