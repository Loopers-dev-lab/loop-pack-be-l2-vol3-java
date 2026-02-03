package com.loopers.domain.user;

import java.util.regex.Pattern;

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
public class Password {

    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^[a-zA-Z0-9!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]+$");

    @Column(name = "password", nullable = false)
    private String value;

    public Password(String value, PasswordEncoder passwordEncoder) {
        validate(value);
        this.value = passwordEncoder.encode(value);
    }

    private void validate(String value) {
        if (value.length() < 8 || value.length() > 16) {
            throw new CoreException(ErrorType.INVALID_PASSWORD_LENGTH);
        }
        if (!PASSWORD_PATTERN.matcher(value).matches()) {
            throw new CoreException(ErrorType.INVALID_PASSWORD_FORMAT);
        }
    }
}
