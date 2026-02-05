package com.loopers.domain.user.vo;

import com.loopers.domain.user.exception.UserValidationException;

import java.util.regex.Pattern;

public record Name(String value) {

    private static final int KOREAN_MAX_LENGTH = 4;
    private static final int ENGLISH_MAX_LENGTH = 50;

    private static final Pattern KOREAN_ONLY_PATTERN = Pattern.compile("^[가-힣]+$");
    private static final Pattern ENGLISH_ONLY_PATTERN = Pattern.compile("^[a-zA-Z]+$");

    private static final String ERROR_NULL_OR_EMPTY = "이름은 필수 입력값입니다";
    private static final String ERROR_INVALID_FORMAT = "이름은 한글 또는 영문만 사용할 수 있습니다 (혼용 불가)";
    private static final String ERROR_KOREAN_MAX_LENGTH = "한글 이름은 4자 이하여야 합니다";
    private static final String ERROR_ENGLISH_MAX_LENGTH = "영문 이름은 50자 이하여야 합니다";

    public Name {
        validate(value);
    }

    private void validate(String name) {
        validateNotEmpty(name);
        validateFormat(name);
    }

    private void validateNotEmpty(String name) {
        if (name == null || name.isBlank()) {
            throw new UserValidationException(ERROR_NULL_OR_EMPTY);
        }
    }

    private void validateFormat(String name) {
        if (isKoreanOnly(name) && name.length() > KOREAN_MAX_LENGTH) {
            throw new UserValidationException(ERROR_KOREAN_MAX_LENGTH);
        }
        if (isKoreanOnly(name)) {
            return;
        }
        if (isEnglishOnly(name) && name.length() > ENGLISH_MAX_LENGTH) {
            throw new UserValidationException(ERROR_ENGLISH_MAX_LENGTH);
        }
        if (isEnglishOnly(name)) {
            return;
        }
        throw new UserValidationException(ERROR_INVALID_FORMAT);
    }

    private boolean isKoreanOnly(String name) {
        return KOREAN_ONLY_PATTERN.matcher(name).matches();
    }

    private boolean isEnglishOnly(String name) {
        return ENGLISH_ONLY_PATTERN.matcher(name).matches();
    }
}
