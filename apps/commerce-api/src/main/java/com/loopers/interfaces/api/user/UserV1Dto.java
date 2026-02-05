package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserInfo;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public class UserV1Dto {

    public record SignupRequest(
        @NotBlank(message = "로그인 ID는 비어있을 수 없습니다.")
        String loginId,

        @NotBlank(message = "비밀번호는 비어있을 수 없습니다.")
        @Size(min = 8, max = 16, message = "비밀번호는 8~16자여야 합니다.")
        String password,

        @NotBlank(message = "이름은 비어있을 수 없습니다.")
        String name,

        @NotNull(message = "생년월일은 비어있을 수 없습니다.")
        LocalDate birthDate,

        @NotBlank(message = "이메일은 비어있을 수 없습니다.")
        @Email(message = "유효하지 않은 이메일 형식입니다.")
        String email
    ) {
    }

    public record SignupResponse(
        String loginId,
        String name,
        LocalDate birthDate,
        String email
    ) {
        public static SignupResponse from(UserInfo info) {
            return new SignupResponse(
                info.loginId(),
                info.name(),
                info.birthDate(),
                info.email()
            );
        }
    }

    public record UserInfoResponse(
        String loginId,
        String name,
        LocalDate birthDate,
        String email
    ) {
        public static UserInfoResponse from(UserInfo info) {
            return new UserInfoResponse(
                info.loginId(),
                info.maskedName(),
                info.birthDate(),
                info.email()
            );
        }
    }
}