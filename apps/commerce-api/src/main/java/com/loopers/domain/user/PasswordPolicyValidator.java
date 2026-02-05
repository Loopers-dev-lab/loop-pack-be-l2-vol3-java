package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class PasswordPolicyValidator {

    private static final DateTimeFormatter BIRTH_DATE_FORMAT_FOR_PASSWORD_CHECK = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String PASSWORD_PATTERN = "^[a-zA-Z0-9!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?~`]{8,16}$";

    private PasswordPolicyValidator() {}

    public static void validate(String password, LocalDate birthDate) {
        if (!password.matches(PASSWORD_PATTERN)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 8~16자의 영문/숫자/특수문자만 가능합니다.");
        }

        String birthDateString = birthDate.format(BIRTH_DATE_FORMAT_FOR_PASSWORD_CHECK);
        if (password.contains(birthDateString)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호에 생년월일을 포함할 수 없습니다.");
        }
    }
}
