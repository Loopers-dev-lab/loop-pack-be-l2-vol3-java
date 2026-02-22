package com.loopers.domain.member.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class Name {

    private static final int MAX_LENGTH = 20;

    private final String value;

    public Name(String value) {
        String trimmed = value != null ? value.trim() : null;
        validate(trimmed);
        this.value = trimmed;
    }

    private void validate(String value) {
        if (value == null || value.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이름은 필수입니다.");
        }

        if (value.length() > MAX_LENGTH) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이름은 " + MAX_LENGTH + "자 이내여야 합니다.");
        }
    }

    public String masked() {
        if (value.length() == 1) {
            return "*";
        }
        return value.substring(0, value.length() - 1) + "*";
    }
}
