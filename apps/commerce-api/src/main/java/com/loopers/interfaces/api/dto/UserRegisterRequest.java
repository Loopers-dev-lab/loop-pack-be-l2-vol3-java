package com.loopers.interfaces.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record UserRegisterRequest(
        @NotBlank String loginId,
        @NotBlank String password,
        @NotBlank String name,
        @NotNull LocalDate birthday,
        @NotBlank @Email String email
) {}
