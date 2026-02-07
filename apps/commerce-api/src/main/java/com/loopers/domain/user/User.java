package com.loopers.domain.user;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Embedded
    private LoginId loginId;

    @Column(name = "password", nullable = false)
    private String password;

    @Embedded
    private UserName name;

    @Embedded
    private BirthDate birthDate;

    @Embedded
    private Email email;

    protected User() {}

    private User(LoginId loginId, String encodedPassword, UserName name, BirthDate birthDate, Email email) {
        this.loginId = loginId;
        this.password = encodedPassword;
        this.name = name;
        this.birthDate = birthDate;
        this.email = email;
    }

    public static User create(LoginId loginId, String encodedPassword, UserName name, BirthDate birthDate, Email email) {
        return new User(loginId, encodedPassword, name, birthDate, email);
    }

    public void changePassword(String newEncodedPassword) {
        this.password = newEncodedPassword;
    }

    public LoginId getLoginId() {
        return this.loginId;
    }

    public String getPassword() {
        return this.password;
    }

    public UserName getName() {
        return this.name;
    }

    public BirthDate getBirthDate() {
        return this.birthDate;
    }

    public Email getEmail() {
        return this.email;
    }
}
