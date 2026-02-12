package com.loopers.interfaces.api.user.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.loopers.application.user.UserInfo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public class UserV1Dto {

    public record CreateRequest(
            @NotBlank(message = "로그인 ID는 필수값입니다.")
            String loginId,
            @NotBlank(message = "비밀번호는 필수값입니다.")
            String password,
            @NotBlank(message = "이름은 필수값입니다.")
            String name,
            @NotNull(message = "생년월일은 필수값입니다.")
            @JsonFormat(pattern = "yyyy-MM-dd")
            LocalDate birthDate,
            @NotBlank(message = "이메일은 필수값입니다.")
            String email
    ) {}

    public record UpdatePasswordRequest(
            @NotBlank(message = "새 비밀번호는 필수값입니다.")
            String newPassword
    ) {}

    public record UserResponse(String loginId, String name, LocalDate birthDate, String email) {
        public static UserResponse from(UserInfo info) {
            return new UserResponse(
                    info.loginId(),
                    info.name(),
                    info.birthDate(),
                    info.email()
            );
        }
    }
}
