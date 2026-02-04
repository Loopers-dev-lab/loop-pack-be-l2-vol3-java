package com.loopers.application.user;

import com.loopers.domain.user.User;

import java.time.LocalDate;

public record UserInfo(String loginId, String name, LocalDate birthDate, String email) {
    public static UserInfo from(User user) {
        return new UserInfo(
            user.getLoginId(),
            maskLastChar(user.getName()),
            user.getBirthDate(),
            user.getEmail()
        );
    }
    private static String maskLastChar(String name) {
        if (name == null || name.isBlank()) return name;
        if (name.length() == 1) return "*";

        return name.substring(0, name.length() - 1) + "*";
    }
}
