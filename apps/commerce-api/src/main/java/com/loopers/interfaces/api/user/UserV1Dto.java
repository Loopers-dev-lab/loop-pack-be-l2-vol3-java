package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserInfo;

public class UserV1Dto {

    public record RegisterRequest(
        String loginId,
        String password,
        String name,
        String birthDate,
        String email
    ) {}

    public record RegisterResponse(Long userId) {
        public static RegisterResponse from(UserInfo info) {
            return new RegisterResponse(info.id());
        }
    }

    public record MeResponse(
        String loginId,
        String name,
        String birthDate,
        String email
    ) {
        public static MeResponse from(UserInfo info) {
            return new MeResponse(
                info.loginId(),
                info.maskedName(),
                info.birthDate(),
                info.email()
            );
        }
    }

    public record ChangePasswordRequest(
        String currentPassword,
        String newPassword
    ) {}

    public record ChangePasswordResponse(String message) {
        public static ChangePasswordResponse success() {
            return new ChangePasswordResponse("비밀번호가 성공적으로 변경되었습니다.");
        }
    }
}
