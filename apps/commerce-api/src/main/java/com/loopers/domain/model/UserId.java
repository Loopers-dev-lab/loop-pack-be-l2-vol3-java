package com.loopers.domain.model;

import lombok.Data;

import java.util.regex.Pattern;

@Data
public class UserId {

    private static final Pattern PATTERN = Pattern.compile("^[a-z0-9]{4,10}$");

    private final String value;

    private UserId(String value) {this.value = value;}

    public static UserId of(String value) {
        if(value == null || value.isBlank()) {
            throw new IllegalArgumentException("로그인 ID는 필수 입력값입니다.");
        }
        String trimmed = value.trim();
        if(!PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException(
                    "로그인 ID는 4~10자의 영문 소문자, 숫자만 가능합니다.");
        }
        return new UserId(trimmed);
    }
}
