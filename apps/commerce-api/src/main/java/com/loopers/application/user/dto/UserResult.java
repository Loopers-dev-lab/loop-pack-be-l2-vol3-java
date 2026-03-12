package com.loopers.application.user.dto;

import com.loopers.domain.user.UserModel;

public record UserResult(
    Long id,
    String loginId,
    String name,
    String birthDate,
    String email
) {
    public static UserResult from(UserModel model) {
        return new UserResult(
                model.getId(),
                model.getLoginId(),
                model.getName(),
                model.getBirthDateString(),
                model.getEmail());
    }
}
