package com.loopers.domain.member.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberId {

    private static final int MAX_LENGTH = 10;
    private static final String PATTERN = "^[a-zA-Z0-9]+$";

    private String value;

    public MemberId(String value) {
        validate(value);
        this.value = value;
    }

    private void validate(String value) {
        if (value == null || value.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "회원 ID는 필수입니다.");
        }

        if (value.length() > MAX_LENGTH) {
            throw new CoreException(ErrorType.BAD_REQUEST, "회원 ID는 " + MAX_LENGTH + "자 이내여야 합니다.");
        }

        if (!value.matches(PATTERN)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "회원 ID는 영문과 숫자만 사용할 수 있습니다.");
        }
    }
}
