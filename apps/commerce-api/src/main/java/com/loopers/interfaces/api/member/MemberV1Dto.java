package com.loopers.interfaces.api.member;

import com.loopers.application.member.MemberInfo;

public class MemberV1Dto {

    public record SignUpRequest(String loginId, String password, String name, String birthday, String email) {
    }

    public record SignUpResponse(Long id, String loginId, String name, String email) {
        public static SignUpResponse from(MemberInfo info) {
            return new SignUpResponse(
                info.id(),
                info.loginId(),
                info.name(),
                info.email()
            );
        }
    }

    public record UpdatePasswordRequest(String currentPassword, String newPassword) {
    }

    public record MyInfoResponse(String loginId, String name, String birthday, String email) {
        public static MyInfoResponse from(MemberInfo info) {
            return new MyInfoResponse(
                info.loginId(),
                info.name(),
                info.birthday().toString(),
                info.email()
            );
        }
    }
}