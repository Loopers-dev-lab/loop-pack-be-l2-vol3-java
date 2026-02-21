package com.loopers.support.error;

import org.springframework.http.HttpStatus;

public enum LikeErrorType implements ErrorType {
    ALREADY_LIKED(HttpStatus.CONFLICT, "이미 좋아요한 항목입니다."),
    LIKE_NOT_FOUND(HttpStatus.NOT_FOUND, "좋아요가 존재하지 않습니다.");

    private final HttpStatus status;
    private final String message;

    LikeErrorType(HttpStatus status, String message) {
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
