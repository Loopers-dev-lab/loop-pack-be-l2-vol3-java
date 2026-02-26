package com.loopers.domain.service;

public interface PasswordEncoder {

    String encrypt(String rawPassword);

    boolean matches(String rawPassword, String encodedPassword);


}
