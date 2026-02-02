package com.loopers.domain.user;

public class FakePasswordEncoder implements PasswordEncoder {

    private static final String ENCODED_PREFIX = "encoded:";

    @Override
    public String encode(String rawPassword) {
        return ENCODED_PREFIX + rawPassword;
    }

    @Override
    public boolean matches(String rawPassword, String encodedPassword) {
        return encodedPassword.equals(ENCODED_PREFIX + rawPassword);
    }
}
