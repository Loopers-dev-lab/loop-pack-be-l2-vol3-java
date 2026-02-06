package com.loopers.support.validation;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public final class MemberValidatorUtil {

    private MemberValidatorUtil() {}

    // Password validation constants
    private static final int PASSWORD_MIN_LENGTH = 8;
    private static final int PASSWORD_MAX_LENGTH = 16;
    private static final String LETTER_PATTERN = ".*[a-zA-Z].*";
    private static final String DIGIT_PATTERN = ".*[0-9].*";
    private static final String SPECIAL_CHAR_PATTERN = ".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*";
    private static final String ALLOWED_CHAR_PATTERN = "^[a-zA-Z0-9!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]+$";
    private static final DateTimeFormatter BIRTH_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    // Login ID validation
    private static final String LOGIN_ID_PATTERN = "^[a-zA-Z0-9]+$";

    // Email validation
    private static final String EMAIL_PATTERN = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$";

    public static void validatePassword(String password, LocalDate birthDate) {
        validatePasswordLength(password);
        validatePasswordAllowedCharacters(password);
        validatePasswordCharacterTypes(password);
        validatePasswordNotContainsBirthDate(password, birthDate);
    }

    private static void validatePasswordLength(String password) {
        if (password.length() < PASSWORD_MIN_LENGTH) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 8자 이상이어야 합니다.");
        }
        if (password.length() > PASSWORD_MAX_LENGTH) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 16자 이하여야 합니다.");
        }
    }

    private static void validatePasswordCharacterTypes(String password) {
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

    private static void validatePasswordNotContainsBirthDate(String password, LocalDate birthDate) {
        String birthDateStr = birthDate.format(BIRTH_DATE_FORMAT);
        if (password.contains(birthDateStr)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호에 생년월일을 포함할 수 없습니다.");
        }
    }

    private static void validatePasswordAllowedCharacters(String password) {
        if (!password.matches(ALLOWED_CHAR_PATTERN)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 영문, 숫자, 특수문자만 사용할 수 있습니다.");
        }
    }

    public static void validateBirthDate(LocalDate birthDate) {
        if (birthDate == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "생년월일은 필수입니다.");
        }
        if (birthDate.isAfter(LocalDate.now())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "생년월일은 미래 날짜일 수 없습니다.");
        }
    }

    public static void validateLoginId(String loginId) {
        if (loginId == null || loginId.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "로그인 ID는 필수입니다.");
        }
        if (!loginId.matches(LOGIN_ID_PATTERN)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "로그인 ID는 영문과 숫자만 사용할 수 있습니다.");
        }
    }

    public static void validateEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이메일은 필수입니다.");
        }
        if (!email.matches(EMAIL_PATTERN)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "올바른 이메일 형식이 아닙니다.");
        }
    }

    public static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이름은 필수입니다.");
        }
    }

    public static void validatePasswordChange(String currentPassword, String newPassword) {
        if (currentPassword.equals(newPassword)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "새 비밀번호는 현재 비밀번호와 달라야 합니다.");
        }
    }
}
