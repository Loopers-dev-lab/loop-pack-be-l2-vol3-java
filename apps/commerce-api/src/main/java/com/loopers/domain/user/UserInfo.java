package com.loopers.domain.user;

import java.time.LocalDate;

public record UserInfo(String loginId, String maskedName, LocalDate birthDate, String email) {

    public static UserInfo from(User user) {
        return new UserInfo(
                user.loginId().asString(),
                user.name().masked(),
                user.birthDate().asLocalDate(),
                user.email().asString()
        );
    }
}
