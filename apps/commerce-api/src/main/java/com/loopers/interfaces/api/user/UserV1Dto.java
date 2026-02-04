package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserInfo;

import java.time.LocalDate;

public class UserV1Dto {

    public record SignupRequest(
            String loginId,
            String password,
            String name,
            String birthDate,
            String email
    ) {}

    public record ChangePasswordRequest(
            String currentPassword,
            String newPassword
    ) {}

    public record UserResponse(
            String loginId,
            String name,
            LocalDate birthDate,
            String email
    ) {
        public static UserResponse fromSignup(UserInfo info) {
            return new UserResponse(info.loginId(), info.name(), info.birthDate(), info.email());
        }

        public static UserResponse fromMe(UserInfo info) {
            return new UserResponse(info.loginId(), info.maskedName(), info.birthDate(), info.email());
        }
    }
}
