package com.loopers.domain.member;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;

@Entity
@Table(name = "member")
public class MemberModel extends BaseEntity {

    @Embedded
    private LoginId loginId;

    @Column(nullable = false)
    private String password;

    @Embedded
    private MemberName name;

    @Column(nullable = false)
    private LocalDate birthDate;

    @Embedded
    private Email email;

    protected MemberModel() {}

    public MemberModel(LoginId loginId, String encodedPassword, MemberName name,
                       LocalDate birthDate, Email email) {
        this.loginId = loginId;
        this.password = encodedPassword;
        this.name = name;
        this.birthDate = birthDate;
        this.email = email;
    }

    public void changePassword(String newEncodedPassword) {
        this.password = newEncodedPassword;
    }

    public boolean matchesPassword(String rawPassword, PasswordEncoder encoder) {
        return encoder.matches(rawPassword, this.password);
    }

    public String encodedPassword() {
        return password;
    }

    public LoginId loginId() {
        return loginId;
    }

    public MemberName name() {
        return name;
    }

    public LocalDate birthDate() {
        return birthDate;
    }

    public Email email() {
        return email;
    }
}
