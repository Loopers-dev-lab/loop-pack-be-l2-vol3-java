package com.loopers.interfaces.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(

    @NotBlank(message = "기존 비밀번호는 필수입니다.")
    String currentPassword,

    @NotBlank(message = "새 비밀번호는 필수입니다.")
    @Size(min = 8, max = 16, message = "비밀번호는 8~16자로 입력해주세요.")
    @Pattern(
        regexp = "^[A-Za-z0-9\\p{P}\\p{S}]+$",
        message = "영문 대소문자, 숫자, 특수문자만 사용 가능합니다."
    )
    String newPassword
) {}
