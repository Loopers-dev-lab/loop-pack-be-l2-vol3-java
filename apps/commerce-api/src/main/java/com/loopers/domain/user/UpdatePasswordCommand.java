package com.loopers.domain.user;

import com.loopers.interfaces.api.user.dto.UserV1Dto;

public record UpdatePasswordCommand(
    String loginId,
    String newPassword
) {
    public static UpdatePasswordCommand from(String loginId, UserV1Dto.UpdatePasswordRequest request) {
        return new UpdatePasswordCommand(loginId, request.newPassword());
    }
}
