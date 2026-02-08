package com.loopers.interfaces.api.user;

/** 사용자 API 요청 DTO */
public class UserRequest {

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
