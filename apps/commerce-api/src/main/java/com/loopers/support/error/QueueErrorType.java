package com.loopers.support.error;

import org.springframework.http.HttpStatus;

public enum QueueErrorType implements ErrorType {
    QUEUE_TOKEN_REQUIRED(HttpStatus.FORBIDDEN, "대기열 토큰이 필요합니다. 대기열에 먼저 진입해주세요."),
    QUEUE_TOKEN_EXPIRED(HttpStatus.FORBIDDEN, "대기열 토큰이 만료되었습니다. 다시 대기열에 진입해주세요."),
    QUEUE_FULL(HttpStatus.SERVICE_UNAVAILABLE, "현재 대기열이 가득 찼습니다. 잠시 후 다시 시도해주세요."),
    QUEUE_NOT_FOUND(HttpStatus.NOT_FOUND, "대기열에 존재하지 않는 사용자입니다."),
    QUEUE_ACTIVATE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "대기열 활성화 처리 중 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String message;

    QueueErrorType(HttpStatus status, String message) {
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
