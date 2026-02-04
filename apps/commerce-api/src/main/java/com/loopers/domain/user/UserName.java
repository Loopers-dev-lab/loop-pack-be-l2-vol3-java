package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.UserErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.regex.Pattern;

/**
 * 이름 Value Object
 *
 * 검증 규칙:
 * - 한글, 영문만 허용
 * - 2~50자
 * - 공백 불허
 *
 * 마스킹 규칙:
 * - 마지막 1글자를 '*'로 대체
 */
@Embeddable
public class UserName {

    private static final int MIN_LENGTH = 2;
    private static final int MAX_LENGTH = 50;
    private static final Pattern PATTERN = Pattern.compile("^[가-힣a-zA-Z]+$");

    @Column(name = "name")
    private String value;

    protected UserName() {}

    public UserName(String value) {
        validate(value);
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public String getMaskedValue() {
        return value.substring(0, value.length() - 1) + "*";
    }

    private void validate(String value) {
        if (value == null || value.isBlank()) {
            throw new CoreException(UserErrorType.INVALID_NAME, "이름은 필수입니다.");
        }

        if (value.length() < MIN_LENGTH || value.length() > MAX_LENGTH) {
            throw new CoreException(UserErrorType.INVALID_NAME,
                    "이름은 " + MIN_LENGTH + "~" + MAX_LENGTH + "자여야 합니다.");
        }

        if (!PATTERN.matcher(value).matches()) {
            throw new CoreException(UserErrorType.INVALID_NAME,
                    "이름은 한글과 영문만 사용할 수 있습니다.");
        }
    }
}