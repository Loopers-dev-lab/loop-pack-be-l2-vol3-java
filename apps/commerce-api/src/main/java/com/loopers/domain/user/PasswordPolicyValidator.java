package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.stereotype.Component;

@Component
public class PasswordPolicyValidator {

    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 16;
    private static final String ALLOWED_PATTERN = "^[a-zA-Z0-9!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?`~]+$";

    public void validate(String password, String birthDate) {
        validateLength(password);
        validateAllowedCharacters(password);
        validateNoBirthDate(password, birthDate);
    }

    private void validateLength(String password) {
        if (password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 8자 이상 16자 이하여야 합니다.");
        }
    }

    private void validateAllowedCharacters(String password) {
        if (!password.matches(ALLOWED_PATTERN)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 영문 대소문자, 숫자, 특수문자만 허용됩니다.");
        }
    }

    private void validateNoBirthDate(String password, String birthDate) {
        String birthDateNumbers = birthDate.replace("-", "");
        if (password.contains(birthDateNumbers)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호에 생년월일을 포함할 수 없습니다.");
        }
    }

    public void validatePasswordChange(String oldPassword, String newPassword) {
        if (oldPassword.equals(newPassword)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "새 비밀번호는 기존 비밀번호와 달라야 합니다.");
        }
    }
}
