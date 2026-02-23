package com.loopers.application.user.command;

import com.loopers.domain.user.vo.UserId;
import lombok.Builder;

@Builder
public record ChangePasswordCommand(
        UserId userId,
        String newRawPassword
) {
    @Override
    public String toString() {
        return "ChangePasswordCommand[userId=%s, newRawPassword=***]".formatted(userId);
    }
}
