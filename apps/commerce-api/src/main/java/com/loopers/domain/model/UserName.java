package com.loopers.domain.model;

import lombok.Data;

import java.util.regex.Pattern;

@Data
public class UserName {

    private static final Pattern PATTERN = Pattern.compile("^[a-zA-Z0-9가-힣]{2,20}$");

    private final String value;
    public UserName(String value) {this.value = value;}

    public static UserName of(String value) {
        if(value == null || value.isEmpty()) {
            throw new IllegalArgumentException("이름은 필수 값입니다.");
        }
        String trimmed = value.trim();
        if(!PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("이름은 2~20자의 한글 또는 영문만 가능합니다.");
        }
        return new UserName(trimmed);
    }
}
