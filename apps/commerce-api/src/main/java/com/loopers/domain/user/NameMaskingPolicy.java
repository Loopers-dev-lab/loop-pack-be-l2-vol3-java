package com.loopers.domain.user;

public class NameMaskingPolicy {

    public static String mask(String name) {
        validateName(name);
        
        String trimmed = name.trim();
        if (trimmed.length() < 2) {
            throw new IllegalArgumentException("이름은 최소 2자 이상이어야 합니다.");
        }
        
        return trimmed.substring(0, trimmed.length() - 1) + "*";
    }

    private static void validateName(String name) {
        if (name == null) {
            throw new IllegalArgumentException("이름은 null일 수 없습니다.");
        }
        
        if (name.isBlank()) {
            throw new IllegalArgumentException("이름은 비어있을 수 없습니다.");
        }
    }
}
