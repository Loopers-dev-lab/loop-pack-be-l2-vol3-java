package com.loopers.interfaces.api.user;

import com.loopers.domain.user.LoginId;
import com.loopers.domain.user.UserInfo;

import java.time.LocalDate;

public class UserDto {

    public record SignupRequest(String loginId, String password, String name, LocalDate birthDate, String email) {
    }

    public record SignupResponse(String loginId) {
        public static SignupResponse from(LoginId loginId) {
            return new SignupResponse(loginId.asString());
        }
    }

    public record ChangePasswordRequest(String newPassword) {
    }

    public record MyInfoResponse(String loginId, String name, LocalDate birthDate, String email) {
        public static MyInfoResponse from(UserInfo userInfo) {
            return new MyInfoResponse(
                    userInfo.loginId(),
                    userInfo.maskedName(),
                    userInfo.birthDate(),
                    userInfo.email()
            );
        }
    }
}
