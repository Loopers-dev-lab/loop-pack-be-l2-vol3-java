package com.loopers.application.user;

public record ChangePasswordCommand(
        String loginId,
        String currentPassword,
        String newPassword
) {}
