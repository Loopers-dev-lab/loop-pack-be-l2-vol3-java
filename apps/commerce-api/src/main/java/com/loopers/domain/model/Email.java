package com.loopers.domain.model;

import lombok.Data;

import java.util.regex.Pattern;

@Data
public class Email {

    private static final Pattern PATTERN = Pattern.compile(
            "^[a-zA-Z0-9]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
    );

    private final String value;

    private Email(String value) {
        this.value = value;
    }

    public static Email of(String value) {
        if(value == null || value.isBlank()) {
            throw new IllegalArgumentException("이메일은 필수 입력값입니다.");
        }
        String trimmed = value.trim();
        if(!PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("올바른 이메일 형식이 아닙니다");
        }
        return new Email(trimmed);
    }
}
