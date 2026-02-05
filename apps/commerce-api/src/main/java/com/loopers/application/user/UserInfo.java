package com.loopers.application.user;

import com.loopers.domain.user.User;

import java.time.LocalDate;

public record UserInfo(
    Long id,
    String loginId,
    String name,
    String maskedName,
    LocalDate birthDate,
    String email
) {
    public static UserInfo from(User user) {
        return new UserInfo(
            user.getId(),
            user.getLoginId(),
            user.getName(),
            user.getMaskedName(),
            user.getBirthDate(),
            user.getEmail()
        );
    }
}
