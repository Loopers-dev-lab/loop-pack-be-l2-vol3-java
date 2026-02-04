package com.loopers.interfaces.api.user;

import com.loopers.domain.user.User;
import jakarta.validation.constraints.NotBlank;

public class UserV1Dto {

    public record RegisterRequest(
        @NotBlank(message = "필수 입력값입니다")
        String loginId,

        @NotBlank(message = "필수 입력값입니다")
        String password,

        @NotBlank(message = "필수 입력값입니다")
        String name,

        @NotBlank(message = "필수 입력값입니다")
        String email,

        @NotBlank(message = "필수 입력값입니다")
        String birthDate
    ) {}

    public record UserResponse(
        Long id,
        String loginId,
        String name,
        String email,
        String birthDate
    ) {
        public static UserResponse from(User user) {
            return new UserResponse(
                user.getId(),
                user.getLoginId(),
                user.getName(),
                user.getEmail(),
                user.getBirthDate()
            );
        }
    }

    public record MeResponse(
        String loginId,
        String name,
        String email,
        String birthDate
    ) {}
}
