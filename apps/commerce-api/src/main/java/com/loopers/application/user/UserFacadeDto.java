package com.loopers.application.user;

import com.loopers.application.user.command.AuthenticateCommand;
import com.loopers.application.user.command.ChangePasswordCommand;
import com.loopers.domain.user.vo.BirthDate;
import com.loopers.domain.user.vo.UserId;
import lombok.Builder;

public class UserFacadeDto {

    @Builder
    public record ChangePasswordRequest(
            UserId userId,
            String currentPassword,
            String newPassword
    ) {
        public AuthenticateCommand toAuthenticateCommand() {
            return AuthenticateCommand.builder()
                    .userId(userId)
                    .rawPassword(currentPassword)
                    .build();
        }

        public ChangePasswordCommand toChangePasswordCommand(BirthDate birthDate) {
            return ChangePasswordCommand.builder()
                    .userId(userId)
                    .newRawPassword(newPassword)
                    .birthDate(birthDate)
                    .build();
        }
    }
}
