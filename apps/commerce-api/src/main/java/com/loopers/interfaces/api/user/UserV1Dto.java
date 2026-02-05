package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserInfo;

import java.time.LocalDate;

public class UserV1Dto {

    public record SignupRequest(
        String loginId,
        String password,
        String name,
        String birthday,
        String email
    ) {}

    public record UserResponse(
        String loginId,
        String name,
        LocalDate birthday,
        String email
    ) {
        public static UserResponse from(UserInfo info) {
            return new UserResponse(
                info.loginId(),
                info.name(),
                info.birthday(),
                info.email()
            );
        }
    }
}
