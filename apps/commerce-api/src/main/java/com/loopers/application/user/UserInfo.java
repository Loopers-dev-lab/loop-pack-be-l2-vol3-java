package com.loopers.application.user;

import com.loopers.domain.user.User;

import java.time.LocalDate;

public record UserInfo(
    Long id,
    String loginId,
    String name,
    LocalDate birthDate,
    String email
) {
    public static UserInfo from(User user) {
        return new UserInfo(
            user.getId(),
            user.getLoginId(),
            user.getName(),
            user.getBirthDate(),
            user.getEmail()
        );
    }

    public String getMaskedName() {
        if (name == null || name.isEmpty()) {
            return name;
        }
        if (name.length() == 1) {
            return "*";
        }
        return name.substring(0, name.length() - 1) + "*";
    }
}
