package com.loopers.application.user;

import com.loopers.interfaces.api.user.dto.UserV1Dto;

import java.time.LocalDate;

public record SignUpCommand(
        String loginId,
        String password,
        String name,
        LocalDate birthDate,
        String email
) {
    public static SignUpCommand from(UserV1Dto.CreateRequest request) {
        return new SignUpCommand(
                request.loginId(),
                request.password(),
                request.name(),
                request.birthDate(),
                request.email()
        );
    }
}
