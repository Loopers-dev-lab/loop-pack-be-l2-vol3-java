package com.loopers.domain.member;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Objects;

@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberName {

    private static final char MASK_CHAR = '*';

    @Column(name = "name", nullable = false)
    private String value;

    public MemberName(String value) {
        if (value == null || value.isBlank()) {
            throw new CoreException(ErrorType.INVALID_NAME);
        }
        this.value = value;
    }

    public String value() {
        return value;
    }

    public String masked() {
        if (value.length() == 1) {
            return String.valueOf(MASK_CHAR);
        }
        return value.substring(0, value.length() - 1) + MASK_CHAR;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MemberName that)) return false;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }
}
