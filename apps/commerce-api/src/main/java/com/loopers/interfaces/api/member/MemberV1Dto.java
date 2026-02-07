package com.loopers.interfaces.api.member;

import com.loopers.domain.member.Gender;
import com.loopers.domain.member.MemberModel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class MemberV1Dto {

    public record RegisterRequest(
        @NotBlank String memberId,
        @NotBlank String password,
        @NotBlank String email,
        @NotBlank String birthDate,
        @NotBlank String name,
        @NotNull Gender gender
    ) {}

    public record MemberResponse(
        Long id,
        String memberId,
        String email,
        String birthDate,
        String name,
        Gender gender
    ) {
        public static MemberResponse from(MemberModel member) {
            return new MemberResponse(
                member.getId(),
                member.getMemberId().value(),
                member.getEmail().address(),
                member.getBirthDate().asString(),
                member.getName().value(),
                member.getGender()
            );
        }
    }

    public record ChangePasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank String newPassword
    ) {}

    public record MeResponse(
        String memberId,
        String name,
        String birthDate,
        String email
    ) {
        public static MeResponse from(MemberModel member) {
            return new MeResponse(
                member.getMemberId().value(),
                maskName(member.getName().value()),
                member.getBirthDate().asString(),
                member.getEmail().address()
            );
        }

        private static String maskName(String name) {
            if (name == null || name.isEmpty()) {
                return name;
            }
            if (name.length() == 1) {
                return "*";
            }
            return name.substring(0, name.length() - 1) + "*";
        }
    }
}
