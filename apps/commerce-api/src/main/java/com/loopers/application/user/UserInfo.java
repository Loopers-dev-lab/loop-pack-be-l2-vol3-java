package com.loopers.application.user;

import com.loopers.domain.user.UserModel;

import java.time.LocalDate;

public record UserInfo(
    String loginId,
    String name,
    String maskedName,
    LocalDate birthDate,
    String email
) {
    public static UserInfo from(UserModel model) {
        return new UserInfo(
            model.getLoginId(),
            model.getName(),
            model.getMaskedName(),
            model.getBirthDate(),
            model.getEmail()
        );
    }
}