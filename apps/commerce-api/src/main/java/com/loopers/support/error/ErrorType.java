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
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, HttpStatus.UNAUTHORIZED.getReasonPhrase(), "로그인을 해주세요."),
    NOT_FOUND(HttpStatus.NOT_FOUND, HttpStatus.NOT_FOUND.getReasonPhrase(), "존재하지 않는 요청입니다."),
    CONFLICT(HttpStatus.CONFLICT, HttpStatus.CONFLICT.getReasonPhrase(), "이미 존재하는 리소스입니다."),

    /** User 도메인 에러 */
    INVALID_EMAIL_FORMAT(HttpStatus.BAD_REQUEST, "INVALID_EMAIL_FORMAT", "이메일은 xx@yy.zz 형식이어야 합니다."),
    INVALID_LOGIN_ID_FORMAT(HttpStatus.BAD_REQUEST, "INVALID_LOGIN_ID_FORMAT", "로그인 ID는 영문과 숫자만 허용됩니다."),
    INVALID_PASSWORD_LENGTH(HttpStatus.BAD_REQUEST, "INVALID_PASSWORD_LENGTH", "비밀번호는 8~16자여야 합니다."),
    INVALID_PASSWORD_FORMAT(HttpStatus.BAD_REQUEST, "INVALID_PASSWORD_FORMAT", "비밀번호는 영문 대소문자, 숫자, 특수문자만 허용됩니다."),
    INVALID_USER_NAME(HttpStatus.BAD_REQUEST, "INVALID_USER_NAME", "이름은 빈 값일 수 없습니다."),
    INVALID_BIRTH_DATE_FORMAT(HttpStatus.BAD_REQUEST, "INVALID_BIRTH_DATE_FORMAT", "생년월일은 yyyy-MM-dd 형식이어야 합니다."),
    DUPLICATE_LOGIN_ID(HttpStatus.BAD_REQUEST, "DUPLICATE_LOGIN_ID", "이미 가입된 로그인 ID입니다."),
    PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "PASSWORD_MISMATCH", "기존 비밀번호가 일치하지 않습니다."),
    PASSWORD_REUSE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "PASSWORD_REUSE_NOT_ALLOWED", "기존 비밀번호와 동일한 비밀번호로 수정할 수 없습니다."),
    BIRTH_DATE_IN_PASSWORD_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "BIRTH_DATE_IN_PASSWORD_NOT_ALLOWED", "비밀번호에 생년월일을 포함할 수 없습니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
