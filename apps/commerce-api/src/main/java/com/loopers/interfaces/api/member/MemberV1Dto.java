package com.loopers.interfaces.api.member;

import com.loopers.application.member.MemberInfo;

import java.time.LocalDate;

public class MemberV1Dto {

    public record SignupRequest(
        String loginId,
        String password,
        String name,
        LocalDate birthDate,
        String email
    ) {}

    public record MemberResponse(
        String loginId,
        String name,
        LocalDate birthDate,
        String email
    ) {
        public static MemberResponse from(MemberInfo info) {
            return new MemberResponse(info.loginId(), info.name(), info.birthDate(), info.email());
        }
    }

    public record ChangePasswordRequest(
        String currentPassword,
        String newPassword
    ) {}
}
