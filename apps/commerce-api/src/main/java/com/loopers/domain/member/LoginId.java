package com.loopers.domain.member;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Objects;
import java.util.regex.Pattern;

@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LoginId {

    private static final Pattern PATTERN = Pattern.compile("^[a-zA-Z0-9]+$");

    @Column(name = "login_id", nullable = false, unique = true)
    private String value;

    public LoginId(String value) {
        if (value == null || value.isBlank() || !PATTERN.matcher(value).matches()) {
            throw new CoreException(ErrorType.INVALID_LOGIN_ID);
        }
        this.value = value;
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LoginId loginId)) return false;
        return Objects.equals(value, loginId.value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }
}
