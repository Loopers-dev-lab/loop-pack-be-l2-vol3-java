package com.loopers.application.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UserRequest() {

    // Command

    public record SignUp(
            @NotBlank(message = "로그인 ID는 필수입니다")
            @Size(min = 4, max = 20, message = "로그인 ID는 4~20자여야 합니다")
            String loginId,
            @NotBlank(message = "비밀번호는 필수입니다")
            String password,
            @NotBlank(message = "이름은 필수입니다")
            @Size(min = 2, max = 20, message = "이름은 2~20자여야 합니다")
            String name,
            @NotNull(message = "생년월일은 필수입니다")
            LocalDate birthDate,
            @NotBlank(message = "이메일은 필수입니다")
            String email
    ) {
    }

    public record ChangePassword(
            @NotBlank(message = "새 비밀번호는 필수입니다")
            String newPassword
    ) {
    }
}
