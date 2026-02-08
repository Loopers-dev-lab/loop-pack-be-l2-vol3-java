package com.loopers.domain.user.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.UserErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.regex.Pattern;

/**
 * 이메일 Value Object
 *
 * 검증 규칙:
 * - 기본 이메일 형식 (local@domain)
 * - 최대 255자
 */
@Embeddable
public class Email {

    private static final int MAX_LENGTH = 255;
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
    );

    @Column(name = "email")
    private String value;

    protected Email() {}

    public Email(String value) {
        validate(value);
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    private void validate(String value) {
        if (value == null || value.isBlank()) {
            throw new CoreException(UserErrorType.INVALID_EMAIL, "이메일은 필수입니다.");
        }

        if (value.length() > MAX_LENGTH) {
            throw new CoreException(UserErrorType.INVALID_EMAIL,
                    "이메일은 최대 " + MAX_LENGTH + "자까지 가능합니다.");
        }

        if (!EMAIL_PATTERN.matcher(value).matches()) {
            throw new CoreException(UserErrorType.INVALID_EMAIL,
                    "이메일 형식이 올바르지 않습니다.");
        }
    }
}
