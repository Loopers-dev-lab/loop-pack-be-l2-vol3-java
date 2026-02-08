package com.loopers.support.error;

import org.springframework.http.HttpStatus;

/**
 * 에러 타입 인터페이스
 *
 * 모든 에러 열거형(UserErrorType, CommonErrorType 등)이 구현하는 공통 계약.
 * getCode()는 enum의 name()을 반환하여 별도 코드 관리 없이 식별한다.
 */
public interface ErrorType {
    HttpStatus getStatus();
    String getCode();
    String getMessage();
}