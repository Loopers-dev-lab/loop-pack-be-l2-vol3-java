package com.loopers.domain.member.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public record Password(String value) {

    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 16;
    private static final String LETTER_PATTERN = ".*[a-zA-Z].*";
    private static final String DIGIT_PATTERN = ".*[0-9].*";
    private static final String SPECIAL_CHAR_PATTERN = ".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*";
    private static final String ALLOWED_CHAR_PATTERN = "^[a-zA-Z0-9!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]+$";

    public Password {
        if (value == null || value.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 비어있을 수 없습니다.");
        }
    }

    public static void validateRawPassword(String rawPassword, String forbiddenKeyword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 비어있을 수 없습니다.");
        }
        validateLength(rawPassword);
        validateAllowedCharacters(rawPassword);
        validateCharacterTypes(rawPassword);
        validateNotContains(rawPassword, forbiddenKeyword);
    }

    private static void validateLength(String password) {
        if (password.length() < MIN_LENGTH) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 8자 이상이어야 합니다.");
        }
        if (password.length() > MAX_LENGTH) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 16자 이하여야 합니다.");
        }
    }

    private static void validateAllowedCharacters(String password) {
        if (!password.matches(ALLOWED_CHAR_PATTERN)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 영문, 숫자, 특수문자만 사용할 수 있습니다.");
        }
    }

    private static void validateCharacterTypes(String password) {
        if (!password.matches(LETTER_PATTERN)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 영문을 포함해야 합니다.");
        }
        if (!password.matches(DIGIT_PATTERN)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 숫자를 포함해야 합니다.");
        }
        if (!password.matches(SPECIAL_CHAR_PATTERN)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 특수문자를 포함해야 합니다.");
        }
    }

    private static void validateNotContains(String password, String forbidden) {
        if (password.contains(forbidden)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호에 생년월일을 포함할 수 없습니다.");
        }
    }
}
