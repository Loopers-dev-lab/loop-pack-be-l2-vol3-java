package com.loopers.domain.member.vo;

import com.loopers.domain.member.PasswordEncoder;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Password {

    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 16;
    private static final String COMPLEXITY_REGEX = "^(?=.*[a-zA-Z])(?=.*\\d)(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]).+$";

    private String value;

    private Password(String encodedValue) {
        this.value = encodedValue;
    }

    public static Password of(String rawPassword, BirthDate birthDate) {
        validate(rawPassword, birthDate);
        return new Password(rawPassword);
    }

    public static Password ofEncoded(String encodedValue) {
        return new Password(encodedValue);
    }

    private static void validate(String rawPassword, BirthDate birthDate) {
        if (rawPassword == null || rawPassword.length() < MIN_LENGTH || rawPassword.length() > MAX_LENGTH) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 " + MIN_LENGTH + "~" + MAX_LENGTH + "자여야 합니다.");
        }

        if (!rawPassword.matches(COMPLEXITY_REGEX)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 영문, 숫자, 특수문자를 포함해야 합니다.");
        }

        checkBirthPatterns(rawPassword, birthDate);
    }

    private static void checkBirthPatterns(String rawPassword, BirthDate birthDate) {
        String plainBirth = birthDate.toPlainString();

        String year = plainBirth.substring(0, 4);
        String yy = plainBirth.substring(2, 4);
        String mmdd = plainBirth.substring(4, 8);

        String[] blackListPatterns = {
                year,
                yy,
                mmdd,
                yy + mmdd,
                year + mmdd
        };

        for (String pattern : blackListPatterns) {
            if (rawPassword.contains(pattern)) {
                throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호에 생년월일과 관련된 숫자(" + pattern + ")를 포함할 수 없습니다.");
            }
        }
    }

    public boolean matches(String rawPassword, PasswordEncoder encoder) {
        return encoder.matches(rawPassword, this.value);
    }

    public Password change(String currentPassword, String newPassword,
                           BirthDate birthDate, PasswordEncoder encoder) {
        if (!matches(currentPassword, encoder)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "현재 비밀번호가 일치하지 않습니다.");
        }

        Password.of(newPassword, birthDate);

        if (matches(newPassword, encoder)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "기존 비밀번호와 동일한 비밀번호는 사용할 수 없습니다.");
        }

        return Password.ofEncoded(encoder.encode(newPassword));
    }
}
