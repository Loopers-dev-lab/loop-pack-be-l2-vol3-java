package com.loopers.domain.user;

public record Email(String value) {
    public Email {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("이메일은 비어있을 수 없습니다.");
        }
        
        if (!value.contains("@")) {
            throw new IllegalArgumentException("이메일은 @를 포함해야 합니다.");
        }
        
        String[] parts = value.split("@");
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new IllegalArgumentException("이메일 형식이 올바르지 않습니다.");
        }
    }
}
