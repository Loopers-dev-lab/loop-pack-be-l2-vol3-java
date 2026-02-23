package com.loopers.application.user.command;

import lombok.Builder;

@Builder
public record RegisterCommand(
        String userId,
        String rawPassword,
        String name,
        String email,
        String birthDate
) {
    @Override
    public String toString() {
        return "RegisterCommand[userId=%s, rawPassword=***, name=%s, email=%s, birthDate=%s]"
                .formatted(userId, name, email, birthDate);
    }
}
