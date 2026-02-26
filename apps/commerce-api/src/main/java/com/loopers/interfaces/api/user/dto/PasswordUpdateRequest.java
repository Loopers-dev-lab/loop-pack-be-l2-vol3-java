package com.loopers.interfaces.api.user.dto;

public record PasswordUpdateRequest(
        String currentPassword,
        String newPassword
) {}
