package com.loopers.user.exception;

public class DuplicateLoginIdException extends RuntimeException {

    private static final String DEFAULT_MESSAGE = "이미 사용 중인 로그인 ID입니다.";

    public DuplicateLoginIdException() {
        super(DEFAULT_MESSAGE);
    }

    public DuplicateLoginIdException(String message) {
        super(message);
    }
}
