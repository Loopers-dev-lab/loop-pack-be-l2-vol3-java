package com.loopers.domain.model.user;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.regex.Pattern;

@Getter
@EqualsAndHashCode
public class UserName {

    private static final Pattern PATTERN = Pattern.compile("^[a-zA-Z0-9가-힣]{2,20}$");

    private final String value;
    private UserName(String value) {this.value = value;}

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

    public String maskedValue() {
        if (value == null || value.isEmpty()) return value;
        if (value.length() == 1) return "*";
        return value.substring(0, value.length() - 1) + "*";
    }
}
