package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

/**
 * 비밀번호 Value Object
 *
 * 검증 규칙:
 * - 8~16자
 * - 영문 대소문자, 숫자, 특수문자만 허용 (공백, 한글 등 불가)
 * - 영문 대문자/소문자/숫자/특수문자 중 3종류 이상 포함
 * - 생년월일 포함 금지 (YYYYMMDD, YYMMDD, MMDD)
 * - 동일 문자 3회 이상 연속 금지 (대소문자 구분 없음)
 * - 연속된 문자/숫자 3자리 이상 금지 (abc, 123 등)
 */
@Embeddable
public class Password {

    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 16;
    private static final int MIN_COMPLEXITY = 3;
    private static final int CONSECUTIVE_LIMIT = 3;
    private static final Pattern ALLOWED_CHARS = Pattern.compile("^[!-~]+$");
    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    @Column(name = "password")
    private String encodedValue;

    protected Password() {}

    private Password(String encodedValue) {
        this.encodedValue = encodedValue;
    }

    public static Password of(String rawPassword, LocalDate birthDate) {
        validate(rawPassword, birthDate);
        return new Password(ENCODER.encode(rawPassword));
    }

    public boolean matches(String rawPassword) {
        return ENCODER.matches(rawPassword, this.encodedValue);
    }

    private static void validate(String rawPassword, LocalDate birthDate) {
        validateNotBlank(rawPassword);
        validateLength(rawPassword);
        validateAllowedChars(rawPassword);
        validateComplexity(rawPassword);
        validateBirthDateNotContained(rawPassword, birthDate);
        validateNoConsecutiveSameChars(rawPassword);
        validateNoSequentialChars(rawPassword);
    }

    private static void validateNotBlank(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 필수입니다.");
        }
    }

    private static void validateLength(String rawPassword) {
        if (rawPassword.length() < MIN_LENGTH || rawPassword.length() > MAX_LENGTH) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                    "비밀번호는 " + MIN_LENGTH + "~" + MAX_LENGTH + "자여야 합니다.");
        }
    }

    private static void validateAllowedChars(String rawPassword) {
        if (!ALLOWED_CHARS.matcher(rawPassword).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                    "비밀번호는 영문 대소문자, 숫자, 특수문자만 사용할 수 있습니다.");
        }
    }

    private static void validateComplexity(String rawPassword) {
        int typeCount = 0;
        if (rawPassword.chars().anyMatch(Character::isUpperCase)) typeCount++;
        if (rawPassword.chars().anyMatch(Character::isLowerCase)) typeCount++;
        if (rawPassword.chars().anyMatch(Character::isDigit)) typeCount++;
        if (rawPassword.chars().anyMatch(c -> !Character.isLetterOrDigit(c))) typeCount++;

        if (typeCount < MIN_COMPLEXITY) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                    "비밀번호는 영문 대문자, 소문자, 숫자, 특수문자 중 " + MIN_COMPLEXITY + "종류 이상 포함해야 합니다.");
        }
    }

    private static void validateBirthDateNotContained(String rawPassword, LocalDate birthDate) {
        String yyyymmdd = birthDate.format(DateTimeFormatter.BASIC_ISO_DATE);
        String yymmdd = yyyymmdd.substring(2);
        String mmdd = yyyymmdd.substring(4);

        if (rawPassword.contains(yyyymmdd) || rawPassword.contains(yymmdd) || rawPassword.contains(mmdd)) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                    "비밀번호에 생년월일을 포함할 수 없습니다.");
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
                throw new CoreException(ErrorType.BAD_REQUEST,
                        "비밀번호에 동일 문자를 " + CONSECUTIVE_LIMIT + "회 이상 연속 사용할 수 없습니다.");
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
                    throw new CoreException(ErrorType.BAD_REQUEST,
                            "비밀번호에 연속된 문자 또는 숫자를 " + CONSECUTIVE_LIMIT + "자리 이상 사용할 수 없습니다.");
                }
            }
        }
    }
}