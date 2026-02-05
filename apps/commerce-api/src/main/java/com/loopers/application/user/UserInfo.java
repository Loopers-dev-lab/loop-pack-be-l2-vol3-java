package com.loopers.application.user;

import com.loopers.domain.user.User;

public record UserInfo(
    Long id,
    String loginId,
    String name,
    String maskedName,
    String birthDate,
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
