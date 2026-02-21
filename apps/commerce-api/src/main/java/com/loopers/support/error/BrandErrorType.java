package com.loopers.support.error;

import org.springframework.http.HttpStatus;

public enum BrandErrorType implements ErrorType {
    BRAND_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 브랜드입니다."),
    ALREADY_DELETED(HttpStatus.CONFLICT, "이미 삭제된 브랜드입니다."),
    INACTIVE_BRAND(HttpStatus.BAD_REQUEST, "비활성 상태의 브랜드입니다.");

    private final HttpStatus status;
    private final String message;

    BrandErrorType(HttpStatus status, String message) {
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
