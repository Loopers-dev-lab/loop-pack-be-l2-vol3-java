package com.loopers.domain.user;

import java.time.LocalDate;

public class UserFixture {
    public static final String VALID_LOGIN_ID = "testUser123";
    public static final String VALID_PASSWORD = "ValidPass1!";
    public static final String VALID_NAME = "홍길동";
    public static final LocalDate VALID_BIRTH_DATE = LocalDate.of(1990, 1, 15);
    public static final String VALID_EMAIL = "test@example.com";

    private String loginId = VALID_LOGIN_ID;
    private String password = VALID_PASSWORD;
    private String name = VALID_NAME;
    private LocalDate birthDate = VALID_BIRTH_DATE;
    private String email = VALID_EMAIL;

    public static UserFixture builder() {
        return new UserFixture();
    }

    public UserFixture loginId(String loginId) {
        this.loginId = loginId;
        return this;
    }

    public UserFixture password(String password) {
        this.password = password;
        return this;
    }

    public UserFixture name(String name) {
        this.name = name;
        return this;
    }

    public UserFixture birthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
        return this;
    }

    public UserFixture email(String email) {
        this.email = email;
        return this;
    }

    public User build() {
        return new User(loginId, password, name, birthDate, email);
    }
}
