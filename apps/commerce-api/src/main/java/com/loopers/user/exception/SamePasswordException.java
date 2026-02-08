package com.loopers.user.exception;

public class SamePasswordException extends RuntimeException {

    private static final String DEFAULT_MESSAGE = "새 비밀번호는 현재 비밀번호와 달라야 합니다.";

    public SamePasswordException() {
        super(DEFAULT_MESSAGE);
    }

    public SamePasswordException(String message) {
        super(message);
    }
}
