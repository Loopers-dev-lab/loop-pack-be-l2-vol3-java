package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserInfo;

import java.time.LocalDate;

/** 사용자 API 응답 DTO */
public class UserResponse {
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

    /** 이름은 개인정보 보호를 위해 maskedName으로 반환한다 */
    public record UserDetailResponse(
            String loginId,
            String name,
            LocalDate birthDate,
            String email
    ) {
        public static UserDetailResponse from(UserInfo info) {
            return new UserDetailResponse(info.loginId(), info.maskedName(), info.birthDate(), info.email());
        }
    }
}
