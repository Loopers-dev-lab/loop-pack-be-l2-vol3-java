package com.loopers.domain.user;

public class UserFixture {

    public static final String DEFAULT_PASSWORD = "Password1!";

    public static User createUser(PasswordEncoder passwordEncoder) {
        return User.signUp("user123", DEFAULT_PASSWORD, "홍길동", "1990-01-01", "test@email.com", passwordEncoder);
    }

    public static PasswordEncoder createPasswordEncoder() {
        return new PasswordEncoder() {
            @Override
            public String encode(String password) {
                return password.toUpperCase();
            }

            @Override
            public boolean matches(String password, String passwordHash) {
                return encode(password).equals(passwordHash);
            }
        };
    }
}
