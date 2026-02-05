package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserInfo;

import java.time.LocalDate;

public class UserV1Dto {

    public record SignUpRequest(
            String loginId,
            String password,
            String name,
            LocalDate birthDate,
            String email
    ) {}

    public record UserResponse(
            String loginId,
            String name,
            LocalDate birthDate,
            String email
    ) {
        public static UserResponse from(UserInfo info) {
            return new UserResponse(
                    info.loginId(),
                    info.maskedName(),
                    info.birthDate(),
                    info.email()
            );
        }
    }

    public record ChangePasswordRequest(
            String newPassword
    ) {}
}
