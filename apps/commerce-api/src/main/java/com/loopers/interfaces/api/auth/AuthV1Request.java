package com.loopers.interfaces.api.auth;

/** 인증 API V1 요청 DTO */
public class AuthV1Request {

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
