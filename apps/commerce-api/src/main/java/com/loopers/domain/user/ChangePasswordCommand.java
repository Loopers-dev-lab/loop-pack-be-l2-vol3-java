package com.loopers.domain.user;

public record ChangePasswordCommand(String loginId, String currentPassword, String newPassword) {}
