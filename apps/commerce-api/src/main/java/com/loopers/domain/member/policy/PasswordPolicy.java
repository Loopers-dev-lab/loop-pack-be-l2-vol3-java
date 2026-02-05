package com.loopers.domain.member.policy;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Pattern;

public class PasswordPolicy {

    private static final Pattern FORMAT_PATTERN =
        Pattern.compile("^[A-Za-z0-9!@#$%^&*()_+=-]{8,16}$");

    public static void validate(String plain, LocalDate birthDate) {
        validateFormat(plain);
        validateNotContainsSubstrings(plain,
            extractBirthDateStrings(birthDate),
            "비밀번호에 생년월일을 포함할 수 없습니다.");
    }

    public static void validateFormat(String plain) {
        if (plain == null || !FORMAT_PATTERN.matcher(plain).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                "비밀번호는 8~16자의 영문, 숫자, 특수문자만 허용됩니다.");
        }
    }

    public static void validateNotContainsSubstrings(
            String plain, List<String> forbidden, String errorMessage) {
        for (String s : forbidden) {
            if (plain.contains(s)) {
                throw new CoreException(ErrorType.BAD_REQUEST, errorMessage);
            }
        }
    }

    public static List<String> extractBirthDateStrings(LocalDate birthDate) {
        return List.of(
            birthDate.format(DateTimeFormatter.ofPattern("yyyyMMdd")),
            birthDate.format(DateTimeFormatter.ofPattern("yyMMdd"))
        );
    }
}
