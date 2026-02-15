package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserInfo;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public class UserV1Dto {

    public record SignupRequest(
        @NotBlank(message = "로그인 ID는 비어있을 수 없습니다.")
        String loginId,

        @NotBlank(message = "비밀번호는 비어있을 수 없습니다.")
        String password,

        @NotBlank(message = "이름은 비어있을 수 없습니다.")
        String name,

        @NotNull(message = "생년월일은 비어있을 수 없습니다.")
        LocalDate birthDate,

        @NotBlank(message = "이메일은 비어있을 수 없습니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        String email
    ) {}

    public record SignupResponse(Long id, String loginId, String name, LocalDate birthDate, String email) {
        public static SignupResponse from(UserInfo info) {
            return new SignupResponse(
                info.id(),
                info.loginId(),
                info.name(),
                info.birthDate(),
                info.email()
            );
        }
    }

    public record ChangePasswordRequest(
        @NotBlank(message = "현재 비밀번호는 비어있을 수 없습니다.")
        String currentPassword,

        @NotBlank(message = "새 비밀번호는 비어있을 수 없습니다.")
        String newPassword
    ) {}

    public record MeResponse(String loginId, String name, LocalDate birthDate, String email) {
        public static MeResponse from(UserInfo info) {
            return new MeResponse(
                info.loginId(),
                info.name(),
                info.birthDate(),
                info.email()
            );
        }
    }
}
