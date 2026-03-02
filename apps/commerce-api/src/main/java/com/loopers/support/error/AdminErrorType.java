package com.loopers.support.error;

import org.springframework.http.HttpStatus;

/** 어드민 인증 관련 에러 타입 */
public enum AdminErrorType implements ErrorType {
    UNAUTHORIZED_ADMIN(HttpStatus.UNAUTHORIZED, "어드민 인증이 필요합니다.");

    private final HttpStatus status;
    private final String message;

    AdminErrorType(HttpStatus status, String message) {
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
