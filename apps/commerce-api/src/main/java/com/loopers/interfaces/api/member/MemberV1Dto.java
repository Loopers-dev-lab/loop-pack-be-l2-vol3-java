package com.loopers.interfaces.api.member;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.loopers.domain.member.MemberModel;

import java.time.LocalDate;

public class MemberV1Dto {

    public record SignUpRequest(
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9]+$") String loginId,
        @NotBlank @Size(min = 8, max = 16) String password,
        @NotBlank String name,
        @NotNull @Past LocalDate birthDate,
        @NotBlank @Email String email
    ) {}

    public record SignUpResponse(
        Long id,
        String loginId,
        String name,
        String email
    ) {}

    public record MyInfoResponse(
        String loginId,
        String name,
        LocalDate birthDate,
        String email
    ) {
        public static MyInfoResponse from(MemberModel member) {
            return new MyInfoResponse(
                member.getLoginId(),
                maskName(member.getName()),
                member.getBirthDate(),
                member.getEmail()
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
        @NotBlank @Size(min = 8, max = 16) String newPassword
    ) {}
}
