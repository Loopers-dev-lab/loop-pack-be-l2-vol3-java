package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.regex.Pattern;

@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Password {

    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^[a-zA-Z0-9`~!@#$%^&*|'\";:\\\\₩?]{8,16}$");

    private String value;

    private Password(String value) {
        this.value = value;
    }

    public static Password of(String value, PasswordEncoder encoder) {
        if (value == null || value.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 비어있을 수 없습니다.");
        }

        if (!PASSWORD_PATTERN.matcher(value).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 8~16자의 영문대소문자, 숫자, 특수문자만 가능합니다.");
        }

        return new Password(encoder.encode(value));
    }

    public String asString() {
        return value;
    }

    // Password는 저장 시 암호화 되므로 Password 간의 동일성/동등성은 PasswordEncoder 가 확인하는 것으로 한다.
    public boolean matches(String raw, PasswordEncoder encoder) {
        return encoder.matches(raw, value);
    }
}
