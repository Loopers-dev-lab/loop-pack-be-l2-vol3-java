package com.loopers.user.dto;

import jakarta.validation.constraints.NotBlank;

public record SignUpRequest(
        @NotBlank
        String id,
        @NotBlank
        String password,
        @NotBlank
        String name,
        @NotBlank
        String birthDate,
        @NotBlank
        String email
) {
}
