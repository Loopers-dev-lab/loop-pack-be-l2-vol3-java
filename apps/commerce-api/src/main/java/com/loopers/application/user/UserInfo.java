package com.loopers.application.user;

import com.loopers.domain.user.User;

import java.time.LocalDate;

public record UserInfo(
        String loginId,
        String name,
        String maskedName,
        LocalDate birthDate,
        String email
) {
    public static UserInfo from(User user) {
        return new UserInfo(
                user.getLoginId().getValue(),
                user.getName().getValue(),
                user.getName().getMaskedValue(),
                user.getBirthDate(),
                user.getEmail().getValue()
        );
    }
}
