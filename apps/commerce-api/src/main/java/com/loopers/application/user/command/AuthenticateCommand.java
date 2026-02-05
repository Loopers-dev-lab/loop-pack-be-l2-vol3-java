package com.loopers.application.user.command;

import com.loopers.domain.user.vo.UserId;
import lombok.Builder;

@Builder
public record AuthenticateCommand(
        UserId userId,
        String rawPassword
) {
}
