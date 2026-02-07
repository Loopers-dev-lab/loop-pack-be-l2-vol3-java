package com.loopers.interfaces.api.auth;

import com.loopers.application.user.UserInfo;

import java.time.LocalDate;

/** 인증 API V1 응답 DTO */
public class AuthV1Response {

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
}
