package com.loopers.domain.user;

public class FakePasswordEncoder implements PasswordEncoder {

    @Override
    public String encode(String rawPassword) {
        return "ENCODED_" + rawPassword;
    }

    @Override
    public boolean matches(String rawPassword, String encodedPassword) {
        return encodedPassword.equals("ENCODED_" + rawPassword);
    }
}
