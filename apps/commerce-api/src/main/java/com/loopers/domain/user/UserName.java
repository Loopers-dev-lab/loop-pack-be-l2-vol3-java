package com.loopers.domain.user;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserName {

    @Column(name = "name", nullable = false)
    private String value;

    public UserName(String value) {
        validate(value);
        this.value = value;
    }

    /**
     * 마지막 글자가 마스킹된 이름을 반환한다.
     *
     * <p>예: "홍길동" → "홍길*", "홍" → "*"</p>
     *
     * @return 마스킹된 이름
     */
    public String masked() {
        if (value.length() == 1) {
            return "*";
        }
        return value.substring(0, value.length() - 1) + "*";
    }

    private void validate(String value) {
        if (value == null || value.isBlank()) {
            throw new CoreException(ErrorType.INVALID_USER_NAME);
        }
    }
}
