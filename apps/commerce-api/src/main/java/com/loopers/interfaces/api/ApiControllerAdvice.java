package com.loopers.interfaces.api;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 전역 예외 처리 컨트롤러 어드바이스.
 *
 * <p>모든 REST API에서 발생하는 예외를 일관된 {@link ApiResponse} 형식으로 변환하여 반환한다.
 * {@link CoreException} 비즈니스 예외, 요청 파라미터 오류, JSON 파싱 오류,
 * 리소스 미발견 및 기타 예외를 처리한다.</p>
 */
@RestControllerAdvice
@Slf4j
public class ApiControllerAdvice {
    /**
     * {@link CoreException} 비즈니스 예외를 처리한다.
     *
     * @param e 비즈니스 로직에서 발생한 CoreException
     * @return 에러 코드와 메시지를 포함한 실패 응답
     */
    @ExceptionHandler
    public ResponseEntity<ApiResponse<?>> handle(CoreException e) {
        log.warn("CoreException : {}", e.getCustomMessage() != null ? e.getCustomMessage() : e.getMessage(), e);
        return failureResponse(e.getErrorType(), e.getCustomMessage());
    }

    /**
     * 요청 파라미터 타입 불일치 예외를 처리한다.
     *
     * @param e 타입 변환 실패 예외
     * @return 잘못된 파라미터 정보를 포함한 BAD_REQUEST 응답
     */
    @ExceptionHandler
    public ResponseEntity<ApiResponse<?>> handleBadRequest(MethodArgumentTypeMismatchException e) {
        String name = e.getName();
        String type = e.getRequiredType() != null ? e.getRequiredType().getSimpleName() : "unknown";
        String value = e.getValue() != null ? e.getValue().toString() : "null";
        String message = String.format("요청 파라미터 '%s' (타입: %s)의 값 '%s'이(가) 잘못되었습니다.", name, type, value);
        return failureResponse(ErrorType.BAD_REQUEST, message);
    }

    /**
     * 필수 요청 파라미터 누락 예외를 처리한다.
     *
     * @param e 필수 파라미터 누락 예외
     * @return 누락된 파라미터 정보를 포함한 BAD_REQUEST 응답
     */
    @ExceptionHandler
    public ResponseEntity<ApiResponse<?>> handleBadRequest(MissingServletRequestParameterException e) {
        String name = e.getParameterName();
        String type = e.getParameterType();
        String message = String.format("필수 요청 파라미터 '%s' (타입: %s)가 누락되었습니다.", name, type);
        return failureResponse(ErrorType.BAD_REQUEST, message);
    }

    /**
     * HTTP 요청 본문 파싱 오류를 처리한다.
     *
     * <p>JSON 타입 불일치, 필수 필드 누락, JSON 매핑 오류 등 다양한 원인을 분석하여
     * 구체적인 에러 메시지를 생성한다.</p>
     *
     * @param e HTTP 메시지 읽기 실패 예외
     * @return 상세한 오류 원인을 포함한 BAD_REQUEST 응답
     */
    @ExceptionHandler
    public ResponseEntity<ApiResponse<?>> handleBadRequest(HttpMessageNotReadableException e) {
        String errorMessage;
        Throwable rootCause = e.getRootCause();

        if (rootCause instanceof InvalidFormatException invalidFormat) {
            String fieldName = invalidFormat.getPath().stream()
                .map(ref -> ref.getFieldName() != null ? ref.getFieldName() : "?")
                .collect(Collectors.joining("."));

            String valueIndicationMessage = "";
            if (invalidFormat.getTargetType().isEnum()) {
                Class<?> enumClass = invalidFormat.getTargetType();
                String enumValues = Arrays.stream(enumClass.getEnumConstants())
                    .map(Object::toString)
                    .collect(Collectors.joining(", "));
                valueIndicationMessage = "사용 가능한 값 : [" + enumValues + "]";
            }

            String expectedType = invalidFormat.getTargetType().getSimpleName();
            Object value = invalidFormat.getValue();

            errorMessage = String.format("필드 '%s'의 값 '%s'이(가) 예상 타입(%s)과 일치하지 않습니다. %s",
                fieldName, value, expectedType, valueIndicationMessage);

        } else if (rootCause instanceof MismatchedInputException mismatchedInput) {
            String fieldPath = mismatchedInput.getPath().stream()
                .map(ref -> ref.getFieldName() != null ? ref.getFieldName() : "?")
                .collect(Collectors.joining("."));
            errorMessage = String.format("필수 필드 '%s'이(가) 누락되었습니다.", fieldPath);

        } else if (rootCause instanceof JsonMappingException jsonMapping) {
            String fieldPath = jsonMapping.getPath().stream()
                .map(ref -> ref.getFieldName() != null ? ref.getFieldName() : "?")
                .collect(Collectors.joining("."));
            errorMessage = String.format("필드 '%s'에서 JSON 매핑 오류가 발생했습니다: %s",
                fieldPath, jsonMapping.getOriginalMessage());

        } else {
            errorMessage = "요청 본문을 처리하는 중 오류가 발생했습니다. JSON 메세지 규격을 확인해주세요.";
        }

        return failureResponse(ErrorType.BAD_REQUEST, errorMessage);
    }

    /**
     * 서버 웹 입력 예외를 처리한다.
     *
     * @param e 서버 웹 입력 예외
     * @return 누락된 요청 값 정보를 포함한 BAD_REQUEST 응답
     */
    @ExceptionHandler
    public ResponseEntity<ApiResponse<?>> handleBadRequest(ServerWebInputException e) {
        String missingParams = extractMissingParameter(e.getReason() != null ? e.getReason() : "");
        if (!missingParams.isEmpty()) {
            String message = String.format("필수 요청 값 '%s'가 누락되었습니다.", missingParams);
            return failureResponse(ErrorType.BAD_REQUEST, message);
        } else {
            return failureResponse(ErrorType.BAD_REQUEST, null);
        }
    }

    /**
     * {@code @RequestParam}/{@code @PathVariable}에 걸린 Bean Validation 위반을 처리한다.
     *
     * @param e 제약 조건 위반 예외
     * @return 위반 메시지를 포함한 BAD_REQUEST 응답
     */
    @ExceptionHandler
    public ResponseEntity<ApiResponse<?>> handleBadRequest(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
            .map(v -> v.getMessage())
            .collect(Collectors.joining(", "));
        return failureResponse(ErrorType.BAD_REQUEST, message.isBlank() ? null : message);
    }

    /**
     * 리소스 미발견 예외를 처리한다.
     *
     * @param e 리소스 미발견 예외
     * @return NOT_FOUND 응답
     */
    @ExceptionHandler
    public ResponseEntity<ApiResponse<?>> handleNotFound(NoResourceFoundException e) {
        return failureResponse(ErrorType.NOT_FOUND, null);
    }

    /**
     * 처리되지 않은 모든 예외의 최종 핸들러.
     *
     * @param e 처리되지 않은 예외
     * @return INTERNAL_ERROR 응답
     */
    @ExceptionHandler
    public ResponseEntity<ApiResponse<?>> handle(Throwable e) {
        log.error("Exception : {}", e.getMessage(), e);
        return failureResponse(ErrorType.INTERNAL_ERROR, null);
    }

    private String extractMissingParameter(String message) {
        Pattern pattern = Pattern.compile("'(.+?)'");
        Matcher matcher = pattern.matcher(message);
        return matcher.find() ? matcher.group(1) : "";
    }

    private ResponseEntity<ApiResponse<?>> failureResponse(ErrorType errorType, String errorMessage) {
        return ResponseEntity.status(errorType.getStatus())
            .body(ApiResponse.fail(errorType.getCode(), errorMessage != null ? errorMessage : errorType.getMessage()));
    }
}
