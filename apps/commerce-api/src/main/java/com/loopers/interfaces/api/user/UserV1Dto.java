package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserInfo;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

public class UserV1Dto {

    public record RegisterRequest(
        @NotBlank(message = "로그인 ID는 필수입니다.")
        @Pattern(regexp = "^[a-zA-Z0-9]+$", message = "로그인 ID는 영문과 숫자만 사용 가능합니다.")
        String loginId,

        @NotBlank(message = "비밀번호는 필수입니다.")
        String password,

        @NotBlank(message = "이름은 필수입니다.")
        String name,

        @NotNull(message = "생년월일은 필수입니다.")
        LocalDate birthDate,

        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        String email
    ) {}

    public record ChangePasswordRequest(
        @NotBlank(message = "현재 비밀번호는 필수입니다.")
        String currentPassword,

        @NotBlank(message = "새 비밀번호는 필수입니다.")
        String newPassword
    ) {}

    public record UserResponse(
        Long id,
        String loginId,
        String name,
        LocalDate birthDate,
        String email
    ) {
        public static UserResponse from(UserInfo info) {
            return new UserResponse(
                info.id(),
                info.loginId(),
                info.maskedName(),
                info.birthDate(),
                info.email()
            );
        }
    }

    public record RegisterResponse(
        Long id,
        String loginId,
        String name,
        LocalDate birthDate,
        String email
    ) {
        public static RegisterResponse from(UserInfo info) {
            return new RegisterResponse(
                info.id(),
                info.loginId(),
                info.name(),
                info.birthDate(),
                info.email()
            );
        }
    }
}
