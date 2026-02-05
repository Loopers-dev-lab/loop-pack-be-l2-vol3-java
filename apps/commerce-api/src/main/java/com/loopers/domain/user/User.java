package com.loopers.domain.user;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "login_id", nullable = false, unique = true))
    private LoginId loginId;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "password", nullable = false))
    private Password password;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "name", nullable = false))
    private Name name;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "birth_date", nullable = false))
    private BirthDate birthDate;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "email", nullable = false))
    private Email email;

    private User(LoginId loginId, Password password, Name name, BirthDate birthDate, Email email) {
        this.loginId = loginId;
        this.password = password;
        this.name = name;
        this.birthDate = birthDate;
        this.email = email;
    }

    public static User create(LoginId loginId, Password password, Name name, BirthDate birthDate, Email email) {
        if (loginId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "로그인ID는 필수입니다.");
        }

        if (password == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 필수입니다.");
        }

        if (name == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이름은 필수입니다.");
        }

        if (birthDate == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "생년월일은 필수입니다.");
        }

        if (email == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이메일은 필수입니다.");
        }

        return new User(loginId, password, name, birthDate, email);
    }

    public LoginId loginId() {
        return loginId;
    }

    public Password password() {
        return password;
    }

    public Name name() {
        return name;
    }

    public BirthDate birthDate() {
        return birthDate;
    }

    public Email email() {
        return email;
    }
}
