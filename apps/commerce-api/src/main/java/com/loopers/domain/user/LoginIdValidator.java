package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.stereotype.Component;

@Component
public class LoginIdValidator {

    private static final String ALPHANUMERIC_PATTERN = "^[a-zA-Z0-9]+$";

    public void validate(String loginId) {
        if (loginId == null || loginId.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "로그인 ID는 필수입니다.");
        }

        if (!loginId.matches(ALPHANUMERIC_PATTERN)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "로그인 ID는 영문과 숫자만 허용됩니다.");
        }
    }
}
