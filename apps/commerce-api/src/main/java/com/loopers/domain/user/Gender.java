package com.loopers.domain.user;

public enum Gender {
    MALE,
    FEMALE;

    public static Gender from(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("성별은 비어있을 수 없습니다.");
        }

        try {
            return Gender.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("성별은 MALE 또는 FEMALE이어야 합니다.", e);
        }
    }
}
