package com.loopers.application.user;

import com.loopers.domain.user.UserModel;
import java.time.LocalDate;

public record UserInfo(Long id, String loginId, String name, String email, LocalDate birthDate) {

    public static UserInfo from(UserModel userModel) {
        return new UserInfo(
            userModel.getId(),
            userModel.getLoginId(),
            userModel.getName(),
            userModel.getEmail(),
            userModel.getBirthDate()
        );
    }
}
