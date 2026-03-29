package com.loopers.support.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorType {
    /** 범용 에러 */
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(), "일시적인 오류가 발생했습니다."),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, HttpStatus.BAD_REQUEST.getReasonPhrase(), "잘못된 요청입니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, HttpStatus.UNAUTHORIZED.getReasonPhrase(), "인증에 실패했습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, HttpStatus.NOT_FOUND.getReasonPhrase(), "존재하지 않는 요청입니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, HttpStatus.FORBIDDEN.getReasonPhrase(), "접근 권한이 없습니다."),
    CONFLICT(HttpStatus.CONFLICT, HttpStatus.CONFLICT.getReasonPhrase(), "이미 존재하는 리소스입니다."),

    QUEUE_FULL(HttpStatus.TOO_MANY_REQUESTS, "Queue Full", "현재 대기열이 가득 찼습니다."),
    QUEUE_TOKEN_REQUIRED(HttpStatus.FORBIDDEN, "Queue Token Required", "대기열 입장 토큰이 필요합니다."),
    QUEUE_TOKEN_INVALID(HttpStatus.FORBIDDEN, "Queue Token Invalid", "유효하지 않은 입장 토큰입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
