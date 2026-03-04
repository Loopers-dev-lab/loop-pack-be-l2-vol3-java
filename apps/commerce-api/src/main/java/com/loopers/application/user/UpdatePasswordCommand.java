package com.loopers.application.user;

import com.loopers.interfaces.api.user.dto.UserV1Dto;

public record UpdatePasswordCommand(
    Long userId,
    String currentPassword,
    String newPassword
) {
    public static UpdatePasswordCommand from(Long userId, String currentPassword, UserV1Dto.UpdatePasswordRequest request) {
        return new UpdatePasswordCommand(userId, currentPassword, request.newPassword());
    }
}
