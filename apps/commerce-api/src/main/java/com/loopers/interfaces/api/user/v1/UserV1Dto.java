package com.loopers.interfaces.api.user.v1;

import jakarta.validation.constraints.NotBlank;

import com.loopers.application.user.UserResult;

public class UserV1Dto {

    public record SignUpRequest(
            @NotBlank(message = "로그인 ID는 필수입니다.")
            String loginId,
            @NotBlank(message = "비밀번호는 필수입니다.")
            String password,
            @NotBlank(message = "이름은 필수입니다.")
            String name,
            @NotBlank(message = "생년월일은 필수입니다.")
            String birthDate,
            @NotBlank(message = "이메일은 필수입니다.")
            String email
    ) {

    }

    public record SignUpResponse(
            Long id,
            String loginId,
            String name,
            String birthDate,
            String email
    ) {

        public static SignUpResponse from(UserResult result) {
            return new SignUpResponse(
                    result.id(),
                    result.loginId(),
                    result.name(),
                    result.birthDate(),
                    result.email()
            );
        }
    }

    public record MeResponse(
            String loginId,
            String name,
            String birthDate,
            String email
    ) {

        public static MeResponse from(UserResult result) {
            return new MeResponse(
                    result.loginId(),
                    result.name(),
                    result.birthDate(),
                    result.email()
            );
        }
    }

    public record UpdatePasswordRequest(
            @NotBlank(message = "기존 비밀번호는 필수입니다.")
            String oldPassword,
            @NotBlank(message = "새 비밀번호는 필수입니다.")
            String newPassword
    ) {

    }
}
