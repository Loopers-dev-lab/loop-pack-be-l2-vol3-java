package com.loopers.interfaces.api.auth;

import com.loopers.application.user.UserInfo;

import java.time.LocalDate;

public class AuthV1Dto {

    public record SignupRequest(
            String loginId,
            String password,
            String name,
            String birthDate,
            String email
    ) {}

    public record SignupResponse(
            String loginId,
            String name,
            LocalDate birthDate,
            String email
    ) {
        public static SignupResponse from(UserInfo info) {
            return new SignupResponse(info.loginId(), info.name(), info.birthDate(), info.email());
        }
    }

    public record ChangePasswordRequest(
            String currentPassword,
            String newPassword
    ) {}
}
