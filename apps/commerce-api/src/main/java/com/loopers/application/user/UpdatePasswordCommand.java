package com.loopers.application.user;

import com.loopers.interfaces.api.user.dto.UserV1Dto;

public record UpdatePasswordCommand(
    String loginId,
    String currentPassword,
    String newPassword
) {
    public static UpdatePasswordCommand from(String loginId, String currentPassword, UserV1Dto.UpdatePasswordRequest request) {
        return new UpdatePasswordCommand(loginId, currentPassword, request.newPassword());
    }
}
