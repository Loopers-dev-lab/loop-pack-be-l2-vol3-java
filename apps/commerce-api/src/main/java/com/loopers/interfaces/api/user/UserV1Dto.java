package com.loopers.interfaces.api.user;

import com.loopers.application.user.ChangePasswordCommand;
import com.loopers.application.user.SignupCommand;
import com.loopers.application.user.UserInfo;

import java.time.LocalDate;

public class UserV1Dto {

    public record SignupRequest(
        String loginId,
        String password,
        String name,
        String birthday,
        String email
    ) {
        public SignupCommand toCommand() {
            return new SignupCommand(loginId, password, name, birthday, email);
        }
    }

    public record ChangePasswordRequest(
        String currentPassword,
        String newPassword
    ) {
        public ChangePasswordCommand toCommand(String loginId) {
            return new ChangePasswordCommand(loginId, currentPassword, newPassword);
        }
    }

    public record UserResponse(
        String loginId,
        String name,
        LocalDate birthday,
        String email
    ) {
        public static UserResponse from(UserInfo info) {
            return new UserResponse(
                info.loginId(),
                maskLastCharacter(info.name()),
                info.birthday(),
                info.email()
            );
        }

        private static String maskLastCharacter(String value) {
            if (value == null || value.length() <= 1) {
                return value;
            }
            return value.substring(0, value.length() - 1) + "*";
        }
    }
}
