package com.loopers.application.user;

import com.loopers.domain.user.UserModel;

import java.time.LocalDate;
import java.util.Objects;

public record UserInfo(
    Long id,
    String loginId,
    String name,
    LocalDate birthday,
    String email
) {
    public static UserInfo from(UserModel userModel) {
        Objects.requireNonNull(userModel, "userModel은 null일 수 없습니다.");
        return new UserInfo(
            userModel.getId(),
            userModel.getLoginId(),
            userModel.getName(),
            userModel.getBirthday(),
            userModel.getEmail()
        );
    }
}
