package com.loopers.domain.user;

public record EncryptedPassword(String value) {

    public EncryptedPassword {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("비밀번호 형식이 올바르지 않습니다");
        }
    }
}
