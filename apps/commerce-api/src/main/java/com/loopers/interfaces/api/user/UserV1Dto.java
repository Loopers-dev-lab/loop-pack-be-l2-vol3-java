package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserInfo;

import java.time.LocalDate;

public class UserV1Dto {

    public record SignupRequest(String loginId, String password, String name, LocalDate birthDate, String email) {
    }

    public record SignupResponse(Long id, String loginId, String name, LocalDate birthDate, String email) {
        public static SignupResponse from(UserInfo info) {
            return new SignupResponse(
                info.id(),
                info.loginId(),
                info.name(),
                info.birthDate(),
                info.email()
            );
        }
    }

    public record ChangePasswordRequest(String currentPassword, String newPassword) {
    }

    public record MeResponse(String loginId, String name, LocalDate birthDate, String email) {
        public static MeResponse from(UserInfo info) {
            return new MeResponse(
                info.loginId(),
                info.name(),
                info.birthDate(),
                info.email()
            );
        }
    }
}
