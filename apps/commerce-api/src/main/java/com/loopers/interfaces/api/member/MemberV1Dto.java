package com.loopers.interfaces.api.member;

import com.loopers.application.member.MemberInfo;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class MemberV1Dto {

    public record SignUpRequest(
        @NotBlank(message = "로그인 ID는 비어있을 수 없습니다.")
        @Pattern(regexp = "^[a-zA-Z0-9]+$", message = "로그인 ID는 영문과 숫자만 입력 가능합니다.")
        String loginId,

        @NotBlank(message = "비밀번호는 비어있을 수 없습니다.")
        @Size(min = 8, max = 16, message = "비밀번호는 8~16자여야 합니다.")
        @Pattern(regexp = "^[a-zA-Z0-9!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?`~]+$", message = "비밀번호는 영문 대소문자, 숫자, 특수문자만 입력 가능합니다.")
        String password,

        @NotBlank(message = "이름은 비어있을 수 없습니다.")
        @Pattern(regexp = "^[가-힣]{2,20}$", message = "이름은 한글 2~20자여야 합니다.")
        String name,

        @NotBlank(message = "생년월일은 비어있을 수 없습니다.")
        @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "생년월일 형식이 올바르지 않습니다. (yyyy-MM-dd)")
        String birthday,

        @NotBlank(message = "이메일은 비어있을 수 없습니다.")
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        String email
    ) {
    }

    public record SignUpResponse(Long id, String loginId, String name, String email) {
        public static SignUpResponse from(MemberInfo info) {
            return new SignUpResponse(
                info.id(),
                info.loginId(),
                info.name(),
                info.email()
            );
        }
    }

    public record UpdatePasswordRequest(
        @NotBlank(message = "현재 비밀번호는 비어있을 수 없습니다.")
        String currentPassword,

        @NotBlank(message = "새 비밀번호는 비어있을 수 없습니다.")
        @Size(min = 8, max = 16, message = "비밀번호는 8~16자여야 합니다.")
        @Pattern(regexp = "^[a-zA-Z0-9!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?`~]+$", message = "비밀번호는 영문 대소문자, 숫자, 특수문자만 입력 가능합니다.")
        String newPassword
    ) {
    }

    public record MyInfoResponse(String loginId, String name, String birthday, String email) {
        public static MyInfoResponse from(MemberInfo info) {
            return new MyInfoResponse(
                info.loginId(),
                info.name(),
                info.birthday().toString(),
                info.email()
            );
        }
    }
}