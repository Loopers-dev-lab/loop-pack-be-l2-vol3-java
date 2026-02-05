package com.loopers.domain.user;

public record UpdatePasswordCommand(
    String loginId,
    String newPassword
) {}
