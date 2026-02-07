package com.loopers.interfaces.api.auth;

import com.loopers.application.user.UserInfo;

import java.time.LocalDate;

/** 인증 API V1 요청/응답 DTO */
public class AuthV1Dto {

    public record SignupRequest(
            String loginId,
            String password,
            String name,
            String birthDate,
            String email
    ) {
        /** 비밀번호 평문 노출 방지 */
        @Override
        public String toString() {
            return "SignupRequest[loginId=" + loginId + ", password=*****, name=" + name
                    + ", birthDate=" + birthDate + ", email=" + email + "]";
        }
    }

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
    ) {
        /** 비밀번호 평문 노출 방지 */
        @Override
        public String toString() {
            return "ChangePasswordRequest[currentPassword=*****, newPassword=*****]";
        }
    }
}
