package com.loopers.interfaces.api.user;

import com.loopers.application.user.command.ChangePasswordCommand;
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
        public String toString() {
            return "RegisterRequest[loginId=%s, password=***, name=%s, birthDate=%s, email=%s, phone=%s]"
                    .formatted(loginId, name, birthDate, email, phone);
        }

        public RegisterCommand toCommand() {
            return RegisterCommand.builder()
                    .userId(loginId)
                    .rawPassword(password)
                    .name(name)
                    .email(email)
                    .birthDate(birthDate)
                    .phone(phone)
                    .build();
        }
    }

    @Builder
    public record UserResponse(
            String loginId,
            String name,
            String email,
            String birthDate,
            String phone
    ) {
        public static UserResponse from(User user) {
            return UserResponse.builder()
                    .loginId(user.id().value())
                    .name(user.getMaskedName())
                    .email(user.email().value())
                    .birthDate(user.birthDate().value().toString())
                    .phone(user.phone() != null ? user.phone().value() : null)
                    .build();
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
            return ChangePasswordCommand.builder()
                    .userId(userId)
                    .newRawPassword(newPassword)
                    .build();
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
