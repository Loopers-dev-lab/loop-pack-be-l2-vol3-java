package com.loopers.domain.member.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public record MemberId(String value) {

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

    public MemberId {
        validate(value);
    }

    private void validate(String memberId) {
        if (memberId == null || memberId.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, ERROR_NULL_OR_EMPTY);
        }
        if (memberId.length() < MIN_LENGTH) {
            throw new CoreException(ErrorType.BAD_REQUEST, ERROR_MIN_LENGTH);
        }
        if (memberId.length() > MAX_LENGTH) {
            throw new CoreException(ErrorType.BAD_REQUEST, ERROR_MAX_LENGTH);
        }
        if (!ALLOWED_CHARS_PATTERN.matcher(memberId).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, ERROR_INVALID_FORMAT);
        }
        if (RESERVED_WORDS.contains(memberId.toLowerCase(Locale.ROOT))) {
            throw new CoreException(ErrorType.BAD_REQUEST, ERROR_RESERVED_WORD);
        }
    }
}
