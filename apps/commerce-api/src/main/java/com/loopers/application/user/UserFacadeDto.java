package com.loopers.application.user;

import com.loopers.application.user.command.AuthenticateCommand;
import com.loopers.application.user.command.ChangePasswordCommand;
import com.loopers.domain.user.vo.BirthDate;
import com.loopers.domain.user.vo.UserId;
import lombok.Builder;
import lombok.Getter;

public class UserFacadeDto {

    @Getter
    @Builder
    public static class ChangePasswordRequest {
        private final UserId userId;
        private final String currentPassword;
        private final String newPassword;

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
