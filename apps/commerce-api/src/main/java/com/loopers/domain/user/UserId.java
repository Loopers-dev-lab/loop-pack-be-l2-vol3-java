package com.loopers.domain.user;

/**
 * 사용자 ID 값 객체.
 * 영문·숫자만 허용, 1~10자 (AGENTS.md / TDD.md 규칙).
 */
public record UserId(String value) {

    private static final int MAX_LENGTH = 10;
    private static final String ALPHANUMERIC_PATTERN = "^[a-zA-Z0-9]+$";

    public UserId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("사용자 ID는 비어있을 수 없습니다.");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("사용자 ID는 최대 10자까지 가능합니다.");
        }
        if (!value.matches(ALPHANUMERIC_PATTERN)) {
            throw new IllegalArgumentException("사용자 ID는 영문자와 숫자로만 구성되어야 합니다.");
        }
    }
}
