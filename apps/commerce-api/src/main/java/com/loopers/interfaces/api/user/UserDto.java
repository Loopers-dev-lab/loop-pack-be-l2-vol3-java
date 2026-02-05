package com.loopers.interfaces.api.user;

import com.loopers.domain.user.LoginId;

import java.time.LocalDate;

public class UserDto {

    public record SignupRequest(String loginId, String password, String name, LocalDate birthDate, String email) {
    }

    public record SignupResponse(String loginId) {
        public static SignupResponse from(LoginId loginId) {
            return new SignupResponse(loginId.asString());
        }
    }
}
