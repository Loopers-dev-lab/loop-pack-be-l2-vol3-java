package com.loopers.domain.member.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class MemberId {

    private static final int MIN_LENGTH = 4;
    private static final int MAX_LENGTH = 10;
    private static final String PATTERN = "^[a-zA-Z0-9]+$";

    private final String value;

    public MemberId(String value) {
        validate(value);
        this.value = value;
    }

    private void validate(String value) {
        if (value == null || value.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "회원 ID는 필수입니다.");
        }

        if (value.length() < MIN_LENGTH || value.length() > MAX_LENGTH) {
            throw new CoreException(ErrorType.BAD_REQUEST, "회원 ID는 " + MIN_LENGTH + "~" + MAX_LENGTH + "자여야 합니다.");
        }

        if (!value.matches(PATTERN)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "회원 ID는 영문과 숫자만 사용할 수 있습니다.");
        }
    }
}
