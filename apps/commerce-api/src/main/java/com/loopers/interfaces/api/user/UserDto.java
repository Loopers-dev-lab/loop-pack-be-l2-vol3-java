package com.loopers.interfaces.api.user;

import com.loopers.application.user.command.ChangePasswordCommand;
import com.loopers.application.user.command.RegisterCommand;
import com.loopers.domain.user.User;
import com.loopers.domain.user.vo.UserId;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

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
            String email,
            @NotBlank(message = "전화번호는 필수입니다")
            @Pattern(regexp = "010-\\d{4}-\\d{4}", message = "전화번호는 010-XXXX-XXXX 형식이어야 합니다")
            String phone
    ) {
        @Override
        public String toString() {
            String maskedPhone = phone != null ? phone.substring(0, 4) + "-****-" + phone.substring(9) : null;
            return "RegisterRequest[loginId=%s, password=***, name=%s, birthDate=%s, email=%s, phone=%s]"
                    .formatted(loginId, name, birthDate, email, maskedPhone);
        }
        public RegisterCommand toCommand() {
            return new RegisterCommand(loginId, password, name, email, birthDate, phone);
        }
    }

    public record UserResponse(
            String loginId,
            String name,
            String email,
            String birthDate,
            String phone
    ) {
        public static UserResponse from(User user) {
            return new UserResponse(
                    user.id().value(),
                    user.getMaskedName(),
                    user.email().value(),
                    user.birthDate().value().toString(),
                    user.phone() != null ? user.phone().value() : null
            );
        }
    }

    public record ChangePasswordRequest(
            @NotBlank(message = "새 비밀번호는 필수입니다")
            String newPassword
    ) {
        @Override
        public String toString() {
            return "ChangePasswordRequest[newPassword=***]";
        }

        public ChangePasswordCommand toCommand(UserId userId) {
            return new ChangePasswordCommand(userId, newPassword);
        }
    }

    public record DuplicateCheckResponse(
            boolean available,
            String loginId
    ) {
        public static DuplicateCheckResponse available(String loginId) {
            return new DuplicateCheckResponse(true, loginId);
        }

        public static DuplicateCheckResponse unavailable(String loginId) {
            return new DuplicateCheckResponse(false, loginId);
        }
    }
}
