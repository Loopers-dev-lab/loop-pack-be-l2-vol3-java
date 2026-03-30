package com.loopers.domain.user.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.Objects;
import java.util.regex.Pattern;

@Embeddable
public class Name {

    private static final Pattern NAME_PATTERN = Pattern.compile("^[가-힣0-9]{2,6}$");

    @Column(name = "name")
    private String value;

    protected Name() {}

    private Name(String value) {
        this.value = value;
    }

    public static Name from(String value) {
        if (value == null || !NAME_PATTERN.matcher(value).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이름은 한글 2~6자만 허용됩니다.");
        }
        return new Name(value);
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Name other)) return false;
        return Objects.equals(value, other.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}
