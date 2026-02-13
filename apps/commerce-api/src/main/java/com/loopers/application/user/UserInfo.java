package com.loopers.application.user;

import com.loopers.domain.user.NameMaskingPolicy;
import com.loopers.domain.user.UserModel;

public record UserInfo(
    String userId,
    String name,
    String email,
    String birthDate,
    String gender
) {
    public static UserInfo from(UserModel user) {
        return new UserInfo(
            user.getUserId(),
            NameMaskingPolicy.mask(user.getUserId()),
            user.getEmail(),
            user.getBirthDate(),
            user.getGender().name()
        );
    }
}
