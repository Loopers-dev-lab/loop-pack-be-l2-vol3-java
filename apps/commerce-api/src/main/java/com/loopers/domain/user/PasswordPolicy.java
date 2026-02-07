package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.UserErrorType;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 비밀번호 교차 검증 정책 (Utility Class)
 *
 * Password VO 자체로는 판단할 수 없는, 다른 도메인 값과의 관계를 검증한다.
 * - 생년월일 포함 금지 (YYYYMMDD, YYMMDD, MMDD)
 */
public final class PasswordPolicy {

    private PasswordPolicy() {}

    public static void validate(String rawPassword, LocalDate birthDate) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new CoreException(UserErrorType.INVALID_PASSWORD, "비밀번호는 필수입니다.");
        }
        if (birthDate == null) {
            throw new CoreException(UserErrorType.INVALID_BIRTH_DATE, "생년월일은 필수입니다.");
        }
        validateBirthDateNotContained(rawPassword, birthDate);
    }

    private static void validateBirthDateNotContained(String rawPassword, LocalDate birthDate) {
        String yyyymmdd = birthDate.format(DateTimeFormatter.BASIC_ISO_DATE);
        String yymmdd = yyyymmdd.substring(2);
        String mmdd = yyyymmdd.substring(4);

        if (rawPassword.contains(yyyymmdd) || rawPassword.contains(yymmdd) || rawPassword.contains(mmdd)) {
            throw new CoreException(UserErrorType.PASSWORD_CONTAINS_BIRTH_DATE);
        }
    }
}
