package com.loopers.interfaces.api.user;

import com.loopers.domain.user.Gender;
import com.loopers.domain.user.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public class UserV1Dto {

    public record RegisterRequest(
        @NotBlank(message = "로그인 ID는 필수입니다")
        String loginId,

        @NotBlank(message = "비밀번호는 필수입니다")
        String password,

        @NotBlank(message = "이름은 필수입니다")
        String name,

        @NotNull(message = "생년월일은 필수입니다")
        LocalDate birthDate,

        @NotBlank(message = "이메일은 필수입니다")
        @Email(message = "이메일 형식이 올바르지 않습니다")
        String email,

        @NotNull(message = "성별은 필수입니다")
        Gender gender
    ) {
    }

    public record RegisterResponse(
        Long id,
        String loginId,
        String name,
        LocalDate birthDate,
        String email,
        Gender gender
    ) {
        public static RegisterResponse from(User user) {
            return new RegisterResponse(
                user.getId(),
                user.getLoginId(),
                user.getName(),
                user.getBirthDate(),
                user.getEmail(),
                user.getGender()
            );
        }
    }
}
