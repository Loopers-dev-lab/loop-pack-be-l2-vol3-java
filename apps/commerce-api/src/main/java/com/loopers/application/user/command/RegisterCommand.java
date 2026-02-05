package com.loopers.application.user.command;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RegisterCommand {
    private final String userId;
    private final String rawPassword;
    private final String name;
    private final String email;
    private final String birthDate;
}
