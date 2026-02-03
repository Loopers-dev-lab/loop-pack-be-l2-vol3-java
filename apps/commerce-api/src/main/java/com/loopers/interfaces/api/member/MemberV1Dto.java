package com.loopers.interfaces.api.member;

import com.loopers.application.member.MemberInfo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public class MemberV1Dto {

    public record RegisterRequest(
        @NotBlank String loginId,
        @NotBlank String password,
        @NotBlank String name,
        @NotNull LocalDate birthDate,
        @NotBlank String email
    ) {}

    public record RegisterResponse(Long id, String loginId, String name, LocalDate birthDate, String email) {
        public static RegisterResponse from(MemberInfo info) {
            return new RegisterResponse(
                info.id(),
                info.loginId(),
                info.name(),
                info.birthDate(),
                info.email()
            );
        }
    }

    public record MemberResponse(String loginId, String name, LocalDate birthDate, String email) {
        public static MemberResponse from(MemberInfo info) {
            return new MemberResponse(
                info.loginId(),
                info.name(),
                info.birthDate(),
                info.email()
            );
        }
    }

    public record ChangePasswordRequest(
        @NotBlank String newPassword
    ) {}
}
