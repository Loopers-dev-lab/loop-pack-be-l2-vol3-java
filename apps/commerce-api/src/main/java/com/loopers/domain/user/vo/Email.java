package com.loopers.domain.user.vo;

import com.loopers.domain.user.exception.UserValidationException;

import java.util.regex.Pattern;

public record Email(String value) {

    private static final int MAX_LENGTH = 254;
    private static final int LOCAL_PART_MAX_LENGTH = 64;

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)+$"
    );
    private static final Pattern CONSECUTIVE_DOTS_PATTERN = Pattern.compile("\\.{2,}");
    private static final Pattern INVALID_LOCAL_CHARS_PATTERN = Pattern.compile("[\\s<>\\[\\]\\\\\"(),;:]");

    private static final String ERROR_NULL_OR_EMPTY = "이메일은 필수 입력값입니다";
    private static final String ERROR_MAX_LENGTH = "이메일은 254자 이하여야 합니다";
    private static final String ERROR_LOCAL_PART_MAX_LENGTH = "이메일 로컬 부분은 64자 이하여야 합니다";
    private static final String ERROR_INVALID_FORMAT = "유효하지 않은 이메일 형식입니다";
    private static final String ERROR_CONSECUTIVE_DOTS = "이메일에 연속된 마침표를 사용할 수 없습니다";
    private static final String ERROR_STARTS_WITH_DOT = "이메일 로컬 부분은 마침표로 시작할 수 없습니다";
    private static final String ERROR_ENDS_WITH_DOT = "이메일 로컬 부분은 마침표로 끝날 수 없습니다";
    private static final String ERROR_INVALID_LOCAL_CHARS = "이메일 로컬 부분에 허용되지 않는 문자가 포함되어 있습니다";

    public Email {
        validate(value);
    }

    private void validate(String email) {
        if (email == null || email.isBlank()) {
            throw new UserValidationException(ERROR_NULL_OR_EMPTY);
        }
        if (email.length() > MAX_LENGTH) {
            throw new UserValidationException(ERROR_MAX_LENGTH);
        }

        int atIndex = email.indexOf('@');
        if (atIndex == -1) {
            throw new UserValidationException(ERROR_INVALID_FORMAT);
        }

        String localPart = email.substring(0, atIndex);

        if (localPart.length() > LOCAL_PART_MAX_LENGTH) {
            throw new UserValidationException(ERROR_LOCAL_PART_MAX_LENGTH);
        }
        if (localPart.startsWith(".")) {
            throw new UserValidationException(ERROR_STARTS_WITH_DOT);
        }
        if (localPart.endsWith(".")) {
            throw new UserValidationException(ERROR_ENDS_WITH_DOT);
        }
        if (CONSECUTIVE_DOTS_PATTERN.matcher(email).find()) {
            throw new UserValidationException(ERROR_CONSECUTIVE_DOTS);
        }
        if (INVALID_LOCAL_CHARS_PATTERN.matcher(localPart).find()) {
            throw new UserValidationException(ERROR_INVALID_LOCAL_CHARS);
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new UserValidationException(ERROR_INVALID_FORMAT);
        }
    }
}
