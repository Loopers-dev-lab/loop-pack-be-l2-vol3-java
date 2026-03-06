package com.loopers.support.error;

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

    public ErrorType getErrorType() {
        return errorType;
    }

    public String getCustomMessage() {
        return customMessage;
    }
}
