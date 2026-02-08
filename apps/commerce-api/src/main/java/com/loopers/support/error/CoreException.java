package com.loopers.support.error;

/**
 * 비즈니스 예외
 *
 * customMessage가 있으면 클라이언트에 해당 메시지를 반환하고,
 * 없으면 ErrorType의 기본 메시지를 반환한다.
 * cause를 통해 원본 예외 체인을 보존하여 로그에서 추적 가능하게 한다.
 */
public class CoreException extends RuntimeException {
    private final ErrorType errorType;
    private final String customMessage;

    public CoreException(ErrorType errorType) {
        this(errorType, null, null);
    }

    public CoreException(ErrorType errorType, String customMessage) {
        this(errorType, customMessage, null);
    }

    public CoreException(ErrorType errorType, String customMessage, Throwable cause) {
        super(customMessage != null ? customMessage : errorType.getMessage(), cause);
        this.errorType = errorType;
        this.customMessage = customMessage;
    }

    public ErrorType getErrorType() {
        return this.errorType;
    }

    public String getCustomMessage() {
        return this.customMessage;
    }
}
