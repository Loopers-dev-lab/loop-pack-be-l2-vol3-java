package com.loopers.interfaces.api.user;

import com.loopers.domain.UserModel;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class UserV1Dto {

    public record SignupRequest(
        @NotBlank(message = "로그인 ID는 필수 입력값입니다.")
        @Size(min = 4, max = 12, message = "로그인 ID는 4자에서 12자 사이여야 합니다.")
        @Pattern(regexp = "^[a-zA-Z0-9]*$", message = "로그인 ID는 영문과 숫자만 허용됩니다.")
        String loginId,

        @NotBlank(message = "비밀번호는 필수 입력값입니다.")
        @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[~!@#$%^&*()_+=-])[A-Za-z\\d~!@#$%^&*()_+=-]{8,16}$",
            message = "비밀번호는 8~16자의 영문 대소문자, 숫자, 특수문자 조합이어야 합니다."
        )
        String password,

        @NotBlank(message = "이름은 필수 입력값입니다.")
        @Size(min = 2, max = 10, message = "이름은 2자에서 10자 사이여야 합니다.")
        String name,

        @NotBlank(message = "생년월일은 필수 입력값입니다.")
        @Pattern(regexp = "^\\d{8}$", message = "생년월일은 yyyyMMdd 형식이어야 합니다.")
        String birthDate,

        @NotBlank(message = "이메일은 필수 입력값입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        String email
    ) {
    }

    public record SignupResponse(
        Long id,
        String loginId,
        String name,
        String birthDate,
        String email
    ) {
        public static SignupResponse from(UserModel model) {
            return new SignupResponse(
                model.getId(),
                model.getLoginId().getValue(),
                model.getName().getMaskedName(),
                model.getBirthDate().toDateString(),
                model.getEmail().getMail()
            );
        }
    }

    public record MyInfoResponse(
        String loginId,
        String name,
        String birthDate,
        String email
    ) {
        public static MyInfoResponse from(UserModel model) {
            return new MyInfoResponse(
                model.getLoginId().getValue(),
                model.getName().getMaskedName(),
                model.getBirthDate().toDateString(),
                model.getEmail().getMail()
            );
        }
    }

    public record ChangePasswordRequest(
        @NotBlank(message = "현재 비밀번호는 필수 입력값입니다.")
        String currentPassword,

        @NotBlank(message = "새 비밀번호는 필수 입력값입니다.")
        @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[~!@#$%^&*()_+=-])[A-Za-z\\d~!@#$%^&*()_+=-]{8,16}$",
            message = "비밀번호는 8~16자의 영문 대소문자, 숫자, 특수문자 조합이어야 합니다."
        )
        String newPassword
    ) {
    }

    public record ChangePasswordResponse(
        String message
    ) {
        public static ChangePasswordResponse success() {
            return new ChangePasswordResponse("비밀번호가 성공적으로 변경되었습니다.");
        }
    }
}
