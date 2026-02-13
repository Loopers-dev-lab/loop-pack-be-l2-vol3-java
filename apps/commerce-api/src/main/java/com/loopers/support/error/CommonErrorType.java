package com.loopers.support.error;

import org.springframework.http.HttpStatus;

/**
 * 공통 에러 타입
 *
 * 특정 도메인에 속하지 않는 범용 HTTP 에러를 정의한다.
 * 도메인별 에러 타입(예: {@link UserErrorType})과 분리하여 관리한다.
 */
public enum CommonErrorType implements ErrorType {
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "일시적인 오류가 발생했습니다."),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 요청입니다."),
    CONFLICT(HttpStatus.CONFLICT, "이미 존재하는 리소스입니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증에 실패했습니다.");

    private final HttpStatus status;
    private final String message;

    CommonErrorType(HttpStatus status, String message) {
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
