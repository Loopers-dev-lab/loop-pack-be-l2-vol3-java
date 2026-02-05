package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserFacadeDto;
import com.loopers.application.user.command.RegisterCommand;
import com.loopers.domain.user.User;
import com.loopers.domain.user.vo.UserId;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Builder;

public class UserDto {

    public record RegisterRequest(
            @NotBlank(message = "로그인 ID는 필수입니다")
            String loginId,

            @NotBlank(message = "비밀번호는 필수입니다")
            String password,

            @NotBlank(message = "이름은 필수입니다")
            String name,

            @NotBlank(message = "생년월일은 필수입니다")
            @Pattern(regexp = "\\d{8}", message = "생년월일은 yyyyMMdd 형식이어야 합니다")
            String birthDate,

            @NotBlank(message = "이메일은 필수입니다")
            @Email(message = "올바른 이메일 형식이 아닙니다")
            String email
    ) {
        public RegisterCommand toCommand() {
            return RegisterCommand.builder()
                    .userId(loginId)
                    .rawPassword(password)
                    .name(name)
                    .email(email)
                    .birthDate(birthDate)
                    .build();
        }
    }

    @Builder
    public record UserResponse(
            String loginId,
            String name,
            String email,
            String birthDate
    ) {
        public static UserResponse from(User user) {
            return UserResponse.builder()
                    .loginId(user.id().value())
                    .name(user.getMaskedName())
                    .email(user.email().value())
                    .birthDate(user.birthDate().value().toString())
                    .build();
        }
    }

    public record ChangePasswordRequest(
            @NotBlank(message = "현재 비밀번호는 필수입니다")
            String currentPassword,

            @NotBlank(message = "새 비밀번호는 필수입니다")
            String newPassword
    ) {
        public UserFacadeDto.ChangePasswordRequest toFacadeRequest(UserId userId) {
            return UserFacadeDto.ChangePasswordRequest.builder()
                    .userId(userId)
                    .currentPassword(currentPassword)
                    .newPassword(newPassword)
                    .build();
        }
    }
}
