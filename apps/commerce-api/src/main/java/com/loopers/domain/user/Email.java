package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.regex.Pattern;

/**
 * 이메일 Value Object
 *
 * 검증 규칙:
 * - RFC 5322 표준 이메일 형식
 * - 최대 255자
 * - 한글/비ASCII 문자 불허
 * - 공백 불허
 * - 로컬 파트 연속 점 불허
 */
@Embeddable
public class Email {

    private static final int MAX_LENGTH = 255;
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]*[a-zA-Z0-9])?(?:\\.[a-zA-Z0-9](?:[a-zA-Z0-9-]*[a-zA-Z0-9])?)*$"
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
            throw new CoreException(ErrorType.BAD_REQUEST, "이메일은 필수입니다.");
        }

        if (value.length() > MAX_LENGTH) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                    "이메일은 최대 " + MAX_LENGTH + "자까지 가능합니다.");
        }

        if (!EMAIL_PATTERN.matcher(value).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                    "이메일 형식이 올바르지 않습니다.");
        }

        validateNoConsecutiveDots(value);
    }

    private void validateNoConsecutiveDots(String value) {
        String localPart = value.substring(0, value.indexOf('@'));
        if (localPart.contains("..")) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                    "이메일 로컬 파트에 연속된 점(.)을 사용할 수 없습니다.");
        }
    }
}