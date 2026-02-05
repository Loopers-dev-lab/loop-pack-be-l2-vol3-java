package com.loopers.domain;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Embeddable;
import java.util.regex.Pattern;
import lombok.EqualsAndHashCode;

@Embeddable
@EqualsAndHashCode
public class Password {
    // 특수문자 범위를 ~!@#$%^&*()_+=- 로 확장했습니다.
    private static final Pattern PASSWORD_PATTERN =
            Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[~!@#$%^&*()_+=-])[A-Za-z\\d~!@#$%^&*()_+=-]{8,16}$");

    private String value;

    protected Password() {}

    public Password(String value) {
        validate(value);
        this.value = value;
    }

    private void validate(String value) {
        if (value == null || !PASSWORD_PATTERN.matcher(value).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 8~16자의 영문 대소문자, 숫자, 특수문자 조합이어야 합니다.");
        }
    }

    public void validateNotContainBirthday(BirthDate birthDate) {
        String birthDateString = birthDate.toDateString();

        if (this.value.contains(birthDateString)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "생년월일은 비밀번호 내에 포함될 수 없습니다.");
        }
    }

    public void validateNotSameAs(Password other) {
        if (this.equals(other)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "현재 사용 중인 비밀번호는 사용할 수 없습니다.");
        }
    }

    public String getValue() {
        return value;
    }
}
