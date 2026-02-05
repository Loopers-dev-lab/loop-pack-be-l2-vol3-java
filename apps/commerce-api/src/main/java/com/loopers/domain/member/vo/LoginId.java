package com.loopers.domain.member.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.Objects;
import java.util.regex.Pattern;

@Embeddable
public class LoginId {

    private static final Pattern PATTERN = Pattern.compile("^[A-Za-z0-9]{1,10}$");

    @Column(name = "login_id", nullable = false, unique = true, length = 20)
    private String value;

    protected LoginId() {}

    public LoginId(String value) {
        if (value == null || !PATTERN.matcher(value).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                "ID는 영문 및 숫자 10자 이내여야 합니다.");
        }
        this.value = value;
    }

    public String value() { return value; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LoginId loginId)) return false;
        return Objects.equals(value, loginId.value);
    }

    @Override
    public int hashCode() { return Objects.hash(value); }
}
