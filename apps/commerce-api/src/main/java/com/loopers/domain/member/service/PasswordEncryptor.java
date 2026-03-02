package com.loopers.domain.member.service;

public interface PasswordEncryptor {

    String encode(String rawPassword);

    boolean matches(String rawPassword, String encodedPassword);
}
