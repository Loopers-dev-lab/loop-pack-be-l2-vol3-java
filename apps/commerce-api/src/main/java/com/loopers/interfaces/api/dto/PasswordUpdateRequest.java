package com.loopers.interfaces.api.dto;

public record PasswordUpdateRequest(
        String currentPassword,
        String newPassword
) {}
