package com.loopers.interfaces.api.member;

import com.loopers.domain.member.Gender;
import com.loopers.domain.member.Member;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public class MemberV1Dto {

    public record SignUpRequest(
        @NotBlank String loginId,
        @NotBlank String password,
        @NotBlank String name,
        @NotBlank String birthDate,
        @NotBlank String email,
        @NotNull Gender gender
    ) {}

    public record SignUpResponse(
        Long id,
        String loginId,
        String name,
        String email,
        Gender gender
    ) {}

    public record MyInfoResponse(
        String loginId,
        String name,
        LocalDate birthDate,
        String email
    ) {
        public static MyInfoResponse from(Member member) {
            return new MyInfoResponse(
                member.getLoginId().value(),
                maskName(member.getName()),
                member.getBirthDate().value(),
                member.getEmail().value()
            );
        }

        private static String maskName(String name) {
            if (name == null || name.length() < 2) {
                return name;
            }
            return name.substring(0, name.length() - 1) + "*";
        }
    }

    public record ChangePasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank String newPassword
    ) {}
}
