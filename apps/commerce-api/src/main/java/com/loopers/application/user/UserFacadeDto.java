package com.loopers.application.user;

import com.loopers.application.user.command.ChangePasswordCommand;
import com.loopers.domain.user.vo.UserId;
import lombok.Builder;

public class UserFacadeDto {

    @Builder
    public record ChangePasswordRequest(
            UserId userId,
            String newPassword
    ) {
        public ChangePasswordCommand toChangePasswordCommand() {
            return ChangePasswordCommand.builder()
                    .userId(userId)
                    .newRawPassword(newPassword)
                    .build();
        }
    }
}
