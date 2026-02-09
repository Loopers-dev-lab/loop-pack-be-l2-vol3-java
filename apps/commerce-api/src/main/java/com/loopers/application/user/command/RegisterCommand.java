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
}
