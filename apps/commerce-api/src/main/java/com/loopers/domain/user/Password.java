package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.Objects;

@Embeddable
public class Password {

    @Column(name = "password", nullable = false)
    private String encryptedValue;

    protected Password() {}

    public Password(String encryptedValue) {
        if (encryptedValue == null || encryptedValue.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "암호화된 비밀번호는 비어있을 수 없습니다.");
        }
        this.encryptedValue = encryptedValue;
    }

    public String getEncryptedValue() {
        return encryptedValue;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Password password)) return false;
        return Objects.equals(encryptedValue, password.encryptedValue);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(encryptedValue);
    }
}
