package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.UserErrorType;

import java.util.regex.Pattern;

/**
 * 비밀번호 Value Object (검증 전용)
 *
 * raw 비밀번호의 자체 규칙만 검증한다.
 * - 암호화는 Service 레이어에서 담당
 * - 교차 검증(생년월일 포함 금지)은 PasswordPolicy Domain Service에서 담당
 *
 * 검증 규칙:
 * - 8~16자
 * - 영문 대소문자, 숫자, 특수문자만 허용 (공백, 한글 등 불가)
 * - 영문 대문자/소문자/숫자/특수문자 중 3종류 이상 포함
 * - 동일 문자 3회 이상 연속 금지 (대소문자 구분 없음)
 * - 연속된 문자/숫자 3자리 이상 금지 (abc, 123 등)
 */
public class Password {

    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 16;
    private static final int MIN_COMPLEXITY = 3;
    private static final int CONSECUTIVE_LIMIT = 3;
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
        validateNotBlank(rawPassword);
        validateLength(rawPassword);
        validateAllowedChars(rawPassword);
        validateComplexity(rawPassword);
        validateNoConsecutiveSameChars(rawPassword);
        validateNoSequentialChars(rawPassword);
    }

    private static void validateNotBlank(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new CoreException(UserErrorType.INVALID_PASSWORD);
        }
    }

    private static void validateLength(String rawPassword) {
        if (rawPassword.length() < MIN_LENGTH || rawPassword.length() > MAX_LENGTH) {
            throw new CoreException(UserErrorType.INVALID_PASSWORD);
        }
    }

    private static void validateAllowedChars(String rawPassword) {
        if (!ALLOWED_CHARS.matcher(rawPassword).matches()) {
            throw new CoreException(UserErrorType.INVALID_PASSWORD);
        }
    }

    private static void validateComplexity(String rawPassword) {
        int typeCount = 0;
        if (rawPassword.chars().anyMatch(Character::isUpperCase)) typeCount++;
        if (rawPassword.chars().anyMatch(Character::isLowerCase)) typeCount++;
        if (rawPassword.chars().anyMatch(Character::isDigit)) typeCount++;
        if (rawPassword.chars().anyMatch(c -> !Character.isLetterOrDigit(c))) typeCount++;

        if (typeCount < MIN_COMPLEXITY) {
            throw new CoreException(UserErrorType.INVALID_PASSWORD);
        }
    }

    private static void validateNoConsecutiveSameChars(String rawPassword) {
        String lower = rawPassword.toLowerCase();
        for (int i = 0; i <= lower.length() - CONSECUTIVE_LIMIT; i++) {
            char target = lower.charAt(i);
            boolean allSame = true;
            for (int j = 1; j < CONSECUTIVE_LIMIT; j++) {
                if (lower.charAt(i + j) != target) {
                    allSame = false;
                    break;
                }
            }
            if (allSame) {
                throw new CoreException(UserErrorType.INVALID_PASSWORD);
            }
        }
    }

    private static void validateNoSequentialChars(String rawPassword) {
        String lower = rawPassword.toLowerCase();
        for (int i = 0; i <= lower.length() - CONSECUTIVE_LIMIT; i++) {
            char c1 = lower.charAt(i);
            char c2 = lower.charAt(i + 1);
            char c3 = lower.charAt(i + 2);

            boolean sameType = (Character.isLetter(c1) && Character.isLetter(c2) && Character.isLetter(c3))
                    || (Character.isDigit(c1) && Character.isDigit(c2) && Character.isDigit(c3));

            if (sameType) {
                boolean ascending = (c2 - c1 == 1) && (c3 - c2 == 1);
                boolean descending = (c1 - c2 == 1) && (c2 - c3 == 1);
                if (ascending || descending) {
                    throw new CoreException(UserErrorType.INVALID_PASSWORD);
                }
            }
        }
    }
}