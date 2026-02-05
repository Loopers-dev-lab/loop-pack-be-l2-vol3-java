package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Password {

    private static final String PASSWORD_PATTERN = "^[a-zA-Z0-9!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]+$";
    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 16;

    @Column(name = "password", nullable = false)
    private String value;

    public Password(String value, LocalDate birthDate) {
        validate(value, birthDate);
        this.value = value;
    }

    private Password(String encodedValue) {
        this.value = encodedValue;
    }

    public static Password ofEncoded(String encodedPassword) {
        if (!StringUtils.hasText(encodedPassword)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 비어있을 수 없습니다.");
        }
        return new Password(encodedPassword);
    }

    public static void validateRawPassword(String rawPassword, LocalDate birthDate) {
        if (!StringUtils.hasText(rawPassword)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 비어있을 수 없습니다.");
        }
        if (rawPassword.length() < MIN_LENGTH || rawPassword.length() > MAX_LENGTH) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 8~16자여야 합니다.");
        }
        if (!rawPassword.matches(PASSWORD_PATTERN)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 영문 대소문자, 숫자, 특수문자만 가능합니다.");
        }
        String birthDateString = birthDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        if (rawPassword.contains(birthDateString)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호에 생년월일을 포함할 수 없습니다.");
        }
    }

    private void validate(String value, LocalDate birthDate) {
        validateRawPassword(value, birthDate);
    }
}
