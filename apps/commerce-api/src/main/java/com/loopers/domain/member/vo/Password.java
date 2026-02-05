package com.loopers.domain.member.vo;

import com.loopers.domain.member.policy.PasswordPolicy;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.Objects;

@Embeddable
public class Password {

    @Column(name = "password", nullable = false)
    private String encoded;

    protected Password() {}

    public Password(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 필수입니다.");
        }
        this.encoded = encoded;
    }

    public static Password create(String plain, LocalDate birthDate,
                                   PasswordEncoder encoder) {
        PasswordPolicy.validate(plain, birthDate);
        return new Password(encoder.encode(plain));
    }

    public boolean matches(String plain, PasswordEncoder encoder) {
        return encoder.matches(plain, this.encoded);
    }

    public String encoded() { return encoded; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Password password)) return false;
        return Objects.equals(encoded, password.encoded);
    }

    @Override
    public int hashCode() { return Objects.hash(encoded); }
}
