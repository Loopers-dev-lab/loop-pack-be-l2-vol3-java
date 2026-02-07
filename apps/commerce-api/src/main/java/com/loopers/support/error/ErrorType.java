package com.loopers.support.error;

import org.springframework.http.HttpStatus;

/**
 * 에러 타입 인터페이스
 *
 * 모든 에러 열거형(UserErrorType, CommonErrorType 등)이 구현하는 공통 계약.
 * ApiControllerAdvice에서 HTTP 상태, 에러 코드, 메시지를 일관되게 추출한다.
 */
public interface ErrorType {
    HttpStatus getStatus();
    String getCode();
    String getMessage();
}