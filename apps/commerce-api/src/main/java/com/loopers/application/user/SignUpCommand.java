package com.loopers.application.user;

import com.loopers.interfaces.api.user.dto.CreateUserRequestV1;

import java.time.LocalDate;

public record SignUpCommand(
        String loginId,
        String password,
        String name,
        LocalDate birthDate,
        String email
) {
    public static SignUpCommand from(CreateUserRequestV1 request) {
        return new SignUpCommand(
                request.getLoginId(),
                request.getPassword(),
                request.getName(),
                request.getBirthDate(),
                request.getEmail()
        );
    }
}
