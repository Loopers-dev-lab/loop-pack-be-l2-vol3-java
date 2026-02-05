package com.loopers.application.user;

import com.loopers.domain.user.UserModel;

import java.time.LocalDate;

public record UserInfo(
    Long id,
    String loginId,
    String name,
    LocalDate birthday,
    String email
) {
    public static UserInfo from(UserModel userModel) {
        return new UserInfo(
            userModel.getId(),
            userModel.getLoginId(),
            userModel.getName(),
            userModel.getBirthday(),
            userModel.getEmail()
        );
    }
}
