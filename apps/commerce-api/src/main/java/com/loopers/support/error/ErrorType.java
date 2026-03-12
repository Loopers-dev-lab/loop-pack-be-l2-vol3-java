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
    CONFLICT(HttpStatus.CONFLICT, HttpStatus.CONFLICT.getReasonPhrase(), "이미 존재하는 리소스입니다."),

    /** Member 도메인 에러 */
    INVALID_LOGIN_ID(HttpStatus.BAD_REQUEST, "Invalid Login Id", "로그인 ID는 영문과 숫자만 가능합니다."),
    INVALID_PASSWORD(HttpStatus.BAD_REQUEST, "Invalid Password", "비밀번호가 규칙에 맞지 않습니다."),
    INVALID_EMAIL(HttpStatus.BAD_REQUEST, "Invalid Email", "이메일 형식이 올바르지 않습니다."),
    INVALID_NAME(HttpStatus.BAD_REQUEST, "Invalid Name", "이름은 필수입니다."),
    DUPLICATE_LOGIN_ID(HttpStatus.CONFLICT, "Duplicate Login Id", "이미 존재하는 로그인 ID입니다."),
    PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "Password Mismatch", "현재 비밀번호가 일치하지 않습니다."),
    PASSWORD_SAME_AS_OLD(HttpStatus.BAD_REQUEST, "Password Same As Old", "현재 비밀번호는 사용할 수 없습니다."),
    PASSWORD_CONTAINS_BIRTH_DATE(HttpStatus.BAD_REQUEST, "Password Contains Birth Date", "비밀번호에 생년월일을 포함할 수 없습니다."),
    MEMBER_NOT_FOUND(HttpStatus.UNAUTHORIZED, "Member Not Found", "회원을 찾을 수 없습니다."),
    AUTHENTICATION_FAILED(HttpStatus.UNAUTHORIZED, "Authentication Failed", "비밀번호가 일치하지 않습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
