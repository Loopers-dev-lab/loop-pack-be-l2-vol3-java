package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Email{

    private static final String EMAIL_PATTERN = "^[A-Za-z0-9+_.-]+@(.+)$";


    @Column(name = "email", nullable = false)
    private String value;

    public Email(String value) {
        validate(value);
        this.value = value;
    }

    private void validate(String value) {
        if (!StringUtils.hasText(value)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "email은  비어있을 수 없습니다.");
        }

        if (!value.matches(EMAIL_PATTERN)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "email 형식이어야 합니다.");
        }

    }
}
