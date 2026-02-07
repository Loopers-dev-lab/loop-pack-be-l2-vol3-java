package com.loopers.support.error;

import org.springframework.http.HttpStatus;

/**
 * 사용자 도메인 에러 타입
 *
 * 에러 코드 체계: USER_{카테고리}{순번}
 * - 0xx: 입력값 검증 실패 (400)
 * - 1xx: 인증 실패 (401)
 * - 2xx: 리소스 미존재 (404)
 * - 3xx: 충돌 (409)
 */
public enum UserErrorType implements ErrorType {
    // 400 Bad Request
    INVALID_LOGIN_ID(HttpStatus.BAD_REQUEST, "USER_001", "로그인 ID 형식이 올바르지 않습니다."),
    INVALID_PASSWORD(HttpStatus.BAD_REQUEST, "USER_002", "비밀번호 형식이 올바르지 않습니다."),
    INVALID_NAME(HttpStatus.BAD_REQUEST, "USER_003", "이름 형식이 올바르지 않습니다."),
    INVALID_BIRTH_DATE(HttpStatus.BAD_REQUEST, "USER_004", "생년월일 형식이 올바르지 않습니다."),
    INVALID_EMAIL(HttpStatus.BAD_REQUEST, "USER_005", "이메일 형식이 올바르지 않습니다."),
    SAME_PASSWORD(HttpStatus.BAD_REQUEST, "USER_006", "현재 비밀번호와 동일한 비밀번호는 사용할 수 없습니다."),
    PASSWORD_CONTAINS_BIRTH_DATE(HttpStatus.BAD_REQUEST, "USER_007", "비밀번호에 생년월일을 포함할 수 없습니다."),

    // 401 Unauthorized
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "USER_101", "인증에 실패했습니다."),
    PASSWORD_MISMATCH(HttpStatus.UNAUTHORIZED, "USER_102", "비밀번호가 일치하지 않습니다."),

    // 404 Not Found
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_201", "존재하지 않는 사용자입니다."),

    // 409 Conflict
    DUPLICATE_LOGIN_ID(HttpStatus.CONFLICT, "USER_301", "이미 사용 중인 로그인 ID입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    UserErrorType(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    @Override
    public HttpStatus getStatus() {
        return this.status;
    }

    @Override
    public String getCode() {
        return this.code;
    }

    @Override
    public String getMessage() {
        return this.message;
    }
}