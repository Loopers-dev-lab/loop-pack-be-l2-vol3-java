package com.loopers.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SignUpRequest(
        @NotBlank
        String id,
        @NotBlank
        String password,
        @NotBlank
        String name,
        @NotBlank
        @Pattern(regexp = "^\\d{8}$", message = "생년월일은 yyyyMMdd 형식이어야 합니다.")
        String birthDate,
        @NotBlank
        @Email
        String email
) {
}
