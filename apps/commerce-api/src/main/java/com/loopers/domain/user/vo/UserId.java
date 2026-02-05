package com.loopers.domain.user.vo;

import com.loopers.domain.user.exception.UserValidationException;

import java.util.Set;
import java.util.regex.Pattern;

public record UserId(String value) {

    private static final int MIN_LENGTH = 4;
    private static final int MAX_LENGTH = 20;

    private static final Pattern ALLOWED_CHARS_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9]*$");

    private static final Set<String> RESERVED_WORDS = Set.of(
            "admin", "administrator", "root", "system", "null", "undefined",
            "support", "help", "info", "contact", "webmaster", "postmaster",
            "api", "www", "ftp", "mail", "email", "test", "guest", "anonymous"
    );

    private static final String ERROR_NULL_OR_EMPTY = "아이디는 필수 입력값입니다";
    private static final String ERROR_MIN_LENGTH = "아이디는 4자 이상이어야 합니다";
    private static final String ERROR_MAX_LENGTH = "아이디는 20자 이하여야 합니다";
    private static final String ERROR_INVALID_FORMAT = "아이디는 영문자로 시작하고, 영문/숫자만 사용할 수 있습니다";
    private static final String ERROR_RESERVED_WORD = "사용할 수 없는 아이디입니다";

    public UserId {
        validate(value);
    }

    private void validate(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new UserValidationException(ERROR_NULL_OR_EMPTY);
        }
        if (userId.length() < MIN_LENGTH) {
            throw new UserValidationException(ERROR_MIN_LENGTH);
        }
        if (userId.length() > MAX_LENGTH) {
            throw new UserValidationException(ERROR_MAX_LENGTH);
        }
        if (!ALLOWED_CHARS_PATTERN.matcher(userId).matches()) {
            throw new UserValidationException(ERROR_INVALID_FORMAT);
        }
        if (RESERVED_WORDS.contains(userId.toLowerCase())) {
            throw new UserValidationException(ERROR_RESERVED_WORD);
        }
    }
}
