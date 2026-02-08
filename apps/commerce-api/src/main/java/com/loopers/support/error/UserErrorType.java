package com.loopers.support.error;

import org.springframework.http.HttpStatus;

/**
 * 사용자 도메인 에러 타입
 *
 * 사용자 관련 비즈니스 에러를 HTTP 상태 코드별로 그룹핑하여 정의한다.
 * - 400: 입력값 형식/정책 위반 (로그인 ID, 비밀번호, 이름, 생년월일, 이메일)
 * - 401: 인증 실패, 비밀번호 불일치
 * - 404: 사용자 미존재
 * - 409: 중복 리소스 (로그인 ID)
 */
public enum UserErrorType implements ErrorType {
    // 400 Bad Request
    INVALID_LOGIN_ID(HttpStatus.BAD_REQUEST, "로그인 ID 형식이 올바르지 않습니다."),
    INVALID_PASSWORD(HttpStatus.BAD_REQUEST, "비밀번호 형식이 올바르지 않습니다."),
    INVALID_NAME(HttpStatus.BAD_REQUEST, "이름 형식이 올바르지 않습니다."),
    INVALID_BIRTH_DATE(HttpStatus.BAD_REQUEST, "생년월일 형식이 올바르지 않습니다."),
    INVALID_EMAIL(HttpStatus.BAD_REQUEST, "이메일 형식이 올바르지 않습니다."),
    SAME_PASSWORD(HttpStatus.BAD_REQUEST, "현재 비밀번호와 동일한 비밀번호는 사용할 수 없습니다."),
    PASSWORD_CONTAINS_BIRTH_DATE(HttpStatus.BAD_REQUEST, "비밀번호에 생년월일을 포함할 수 없습니다."),

    // 401 Unauthorized
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증에 실패했습니다."),
    PASSWORD_MISMATCH(HttpStatus.UNAUTHORIZED, "비밀번호가 일치하지 않습니다."),

    // 404 Not Found
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 사용자입니다."),

    // 409 Conflict
    DUPLICATE_LOGIN_ID(HttpStatus.CONFLICT, "이미 사용 중인 로그인 ID입니다.");

    private final HttpStatus status;
    private final String message;

    UserErrorType(HttpStatus status, String message) {
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
