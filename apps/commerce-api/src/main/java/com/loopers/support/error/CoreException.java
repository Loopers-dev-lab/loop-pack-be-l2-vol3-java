package com.loopers.support.error;

import lombok.Getter;

@Getter
public class CoreException extends RuntimeException {
    private final ErrorType errorType;
    private final String customMessage;

    public CoreException(ErrorType errorType) {
        this(errorType, null);
    }

    public CoreException(ErrorType errorType, String customMessage) {
        super(customMessage != null ? customMessage : errorType.getMessage());
        this.errorType = errorType;
        this.customMessage = customMessage;
    }

    /**
     * 원인 예외를 보존하여 스택트레이스 추적이 가능하도록 한다.
     */
    public CoreException(ErrorType errorType, String customMessage, Throwable cause) {
        super(customMessage != null ? customMessage : errorType.getMessage(), cause);
        this.errorType = errorType;
        this.customMessage = customMessage;
    }
}
