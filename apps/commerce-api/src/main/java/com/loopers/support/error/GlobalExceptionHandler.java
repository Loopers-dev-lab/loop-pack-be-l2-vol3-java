package com.loopers.support.error;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * 전역 예외 처리기
 *
 * ⚠️ DEPRECATED: 더 이상 사용되지 않음
 * - CoreException으로 전환됨
 * - ApiControllerAdvice가 모든 예외 처리
 *
 * @RestControllerAdvice: 모든 Controller의 예외를 처리
 *
 * 역할:
 * - 예외를 HTTP 상태 코드와 에러 메시지로 변환
 * - 일관된 에러 응답 형식 제공
 * - 민감한 정보 노출 방지
 */
// @RestControllerAdvice  // 비활성화: ApiControllerAdvice 사용
public class GlobalExceptionHandler {

    /**
     * 에러 응답 DTO
     */
    @Getter
    @AllArgsConstructor
    public static class ErrorResponse {
        private String message;
        private Map<String, String> errors;  // 필드별 에러 (Validation용)

        public ErrorResponse(String message) {
            this.message = message;
            this.errors = null;
        }
    }

    // ========================================
    // 1. 비즈니스 로직 예외 처리
    // ========================================

    /**
     * IllegalArgumentException 처리
     *
     * 사용 케이스:
     * - 중복 로그인 ID → 409 CONFLICT
     * - 잘못된 입력 → 400 BAD_REQUEST
     * - 인증 실패 → 401 UNAUTHORIZED
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException e) {
        String message = e.getMessage();

        // 메시지 기반으로 HTTP 상태 코드 결정
        if (message.contains("중복") || message.contains("이미 사용 중")) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse(message));
        }

        if (message.contains("로그인 ID 또는 비밀번호") ||
                message.contains("인증") ||
                message.contains("일치하지 않습니다")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse(message));
        }

        // 기본: 400 BAD_REQUEST
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(message));
    }

    // ========================================
    // 2. Validation 예외 처리
    // ========================================

    /**
     * Bean Validation 실패 처리
     *
     * @Valid 애노테이션에서 발생하는 예외
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e) {
        Map<String, String> errors = new HashMap<>();

        // 각 필드의 에러 메시지 수집
        e.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        return ResponseEntity.badRequest()
                .body(new ErrorResponse("입력값이 올바르지 않습니다.", errors));
    }

    // ========================================
    // 3. 인증 헤더 관련 예외 처리
    // ========================================

    /**
     * 필수 헤더 누락 처리
     *
     * @RequestHeader(required = true)에서 발생
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeaderException(MissingRequestHeaderException e) {
        String headerName = e.getHeaderName();
        String message = String.format("필수 헤더가 누락되었습니다: %s", headerName);

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse(message));
    }

    // ========================================
    // 4. 기타 예외 처리
    // ========================================

    /**
     * 그 외 모든 예외 처리
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(Exception e) {
        // 운영 환경에서는 상세 에러 메시지를 숨김
        String message = "서버 내부 오류가 발생했습니다.";

        // 개발 환경에서만 상세 에러 로깅
        e.printStackTrace();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(message));
    }
}
