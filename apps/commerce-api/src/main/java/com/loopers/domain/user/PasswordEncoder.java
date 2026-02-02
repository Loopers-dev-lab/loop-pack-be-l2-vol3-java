package com.loopers.domain.user;

@FunctionalInterface
public interface PasswordEncoder {
    String encode(String rawPassword);
}