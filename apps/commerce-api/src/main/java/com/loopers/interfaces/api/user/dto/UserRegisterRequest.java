package com.loopers.interfaces.api.user.dto;

import com.loopers.application.user.RegisterUseCase.RegisterCommand;

import java.time.LocalDate;

public record UserRegisterRequest(
        String loginId,
        String password,
        String name,
        LocalDate birthday,
        String email
) {
    public RegisterCommand toCommand() {
        return new RegisterCommand(loginId, name, password, birthday, email);
    }
}
