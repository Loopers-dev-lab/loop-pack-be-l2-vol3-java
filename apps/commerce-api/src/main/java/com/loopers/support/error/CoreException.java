package com.loopers.support.error;

import lombok.Getter;

/**
 * 비즈니스 예외를 나타내는 커스텀 런타임 예외 클래스.
 * 모든 비즈니스 예외는 {@link ErrorType}을 통해 HTTP 상태 코드, 에러 코드, 메시지를 포함한다.
 */
@Getter
public class CoreException extends RuntimeException {
    private final ErrorType errorType;
    private final String customMessage;

    /**
     * ErrorType으로 비즈니스 예외를 생성한다.
     *
     * @param errorType 에러 유형
     */
    public CoreException(ErrorType errorType) {
        this(errorType, null);
    }

    /**
     * ErrorType과 커스텀 메시지로 비즈니스 예외를 생성한다.
     *
     * @param errorType     에러 유형
     * @param customMessage 커스텀 에러 메시지 (null이면 ErrorType의 기본 메시지 사용)
     */
    public CoreException(ErrorType errorType, String customMessage) {
        super(customMessage != null ? customMessage : errorType.getMessage());
        this.errorType = errorType;
        this.customMessage = customMessage;
    }
}
