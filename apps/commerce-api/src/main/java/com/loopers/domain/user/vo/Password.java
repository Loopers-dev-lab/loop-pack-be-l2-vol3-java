package com.loopers.domain.user.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.UserErrorType;

import java.util.regex.Pattern;

/**
 * 비밀번호 Value Object (검증 전용)
 *
 * raw 비밀번호의 자체 규칙만 검증한다.
 * - 암호화는 Service 레이어에서 담당
 * - 교차 검증(생년월일 포함 금지)은 UserService에서 담당
 *
 * 검증 규칙:
 * - 8~16자
 * - 영문 대소문자, 숫자, 특수문자만 허용
 * - 영문 대문자/소문자/숫자/특수문자 중 3종류 이상 포함
 */
public class Password {

    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 16;
    private static final int MIN_COMPLEXITY = 3;
    private static final Pattern ALLOWED_CHARS = Pattern.compile("^[!-~]+$");

    private final String value;

    private Password(String value) {
        this.value = value;
    }

    public static Password of(String rawPassword) {
        validate(rawPassword);
        return new Password(rawPassword);
    }

    public String getValue() {
        return value;
    }

    private static void validate(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new CoreException(UserErrorType.INVALID_PASSWORD);
        }

        if (rawPassword.length() < MIN_LENGTH || rawPassword.length() > MAX_LENGTH) {
            throw new CoreException(UserErrorType.INVALID_PASSWORD);
        }

        if (!ALLOWED_CHARS.matcher(rawPassword).matches()) {
            throw new CoreException(UserErrorType.INVALID_PASSWORD);
        }

        int typeCount = 0;
        if (rawPassword.chars().anyMatch(Character::isUpperCase)) typeCount++;
        if (rawPassword.chars().anyMatch(Character::isLowerCase)) typeCount++;
        if (rawPassword.chars().anyMatch(Character::isDigit)) typeCount++;
        if (rawPassword.chars().anyMatch(c -> !Character.isLetterOrDigit(c))) typeCount++;

        if (typeCount < MIN_COMPLEXITY) {
            throw new CoreException(UserErrorType.INVALID_PASSWORD);
        }
    }
}
