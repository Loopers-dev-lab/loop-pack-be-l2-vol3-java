package com.loopers.application.user;

import com.loopers.domain.user.User;

public record UserResult(
        Long id,
        String loginId,
        String name,
        String birthDate,
        String email
) {

    public static UserResult from(User user) {
        return new UserResult(
                user.getId(),
                user.getLoginId().getValue(),
                user.getName().masked(),
                user.getBirthDate().getValue(),
                user.getEmail().getValue()
        );
    }
}
