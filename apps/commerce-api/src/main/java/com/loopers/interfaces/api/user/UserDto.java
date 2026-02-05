package com.loopers.interfaces.api.user;

import com.loopers.application.user.command.RegisterCommand;
import com.loopers.domain.user.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class UserDto {

    @Getter
    @NoArgsConstructor
    public static class RegisterRequest {
        @NotBlank(message = "로그인 ID는 필수입니다")
        private String loginId;

        @NotBlank(message = "비밀번호는 필수입니다")
        private String password;

        @NotBlank(message = "이름은 필수입니다")
        private String name;

        @NotBlank(message = "이메일은 필수입니다")
        @Email(message = "올바른 이메일 형식이 아닙니다")
        private String email;

        @NotBlank(message = "생년월일은 필수입니다")
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "생년월일은 yyyy-MM-dd 형식이어야 합니다")
        private String birthDate;

        public RegisterRequest(String loginId, String password, String name, String email, String birthDate) {
            this.loginId = loginId;
            this.password = password;
            this.name = name;
            this.email = email;
            this.birthDate = birthDate;
        }

        public RegisterCommand toCommand() {
            return new RegisterCommand(loginId, password, name, email, birthDate);
        }
    }

    @Getter
    public static class UserResponse {
        private final String loginId;
        private final String name;
        private final String email;
        private final String birthDate;

        private UserResponse(String loginId, String name, String email, String birthDate) {
            this.loginId = loginId;
            this.name = name;
            this.email = email;
            this.birthDate = birthDate;
        }

        public static UserResponse from(User user) {
            return new UserResponse(
                    user.id().value(),
                    user.getMaskedName(),
                    user.email().value(),
                    user.birthDate().value().toString()
            );
        }
    }
}
