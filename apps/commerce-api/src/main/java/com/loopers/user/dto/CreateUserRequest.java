package com.loopers.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateUserRequest(
        @NotBlank
        @Pattern(regexp = "^[a-zA-Z0-9]+$")
        String loginId,
        @NotBlank
        String password,
        @NotBlank
        String name,
        @NotBlank
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}")
        String birthDate,
        @Email
        @NotBlank
        String email
) {
}
