package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.regex.Pattern;

/**
 * 로그인 ID Value Object
 *
 * 검증 규칙:
 * - 영문 대소문자 + 숫자만 허용
 * - 4~20자
 * - 영문으로 시작
 */
@Embeddable
public class LoginId {

    private static final int MIN_LENGTH = 4;
    private static final int MAX_LENGTH = 20;
    private static final Pattern PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9]{3,19}$");

    @Column(name = "login_id")
    private String value;

    // JPA 기본 생성자
    protected LoginId() {}

    public LoginId(String value) {
        validate(value);
        this.value = value;
    }

    private void validate(String value) {
        if (value == null || value.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "로그인 ID는 필수입니다.");
        }

        if (value.length() < MIN_LENGTH || value.length() > MAX_LENGTH) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                    "로그인 ID는 " + MIN_LENGTH + "~" + MAX_LENGTH + "자여야 합니다.");
        }

        if (!PATTERN.matcher(value).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                    "로그인 ID는 영문으로 시작하고, 영문과 숫자만 사용할 수 있습니다.");
        }
    }

    public String getValue() {
        return value;
    }
}
