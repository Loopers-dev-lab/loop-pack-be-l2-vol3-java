package com.loopers.application.user;

import com.loopers.domain.user.UserModel;

import java.time.LocalDate;

public record UserInfo(
    String loginId,
    String name,
    LocalDate birthDate,
    String email
) {
    public static UserInfo from(UserModel user) {
        return new UserInfo(
            user.getLoginId(),
            maskName(user.getName()),
            user.getBirthDate(),
            user.getEmail().getValue()
        );
    }

    private static String maskName(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        if (name.length() == 1) {
            return "*";
        }
        return name.substring(0, name.length() - 1) + "*";
    }
}
