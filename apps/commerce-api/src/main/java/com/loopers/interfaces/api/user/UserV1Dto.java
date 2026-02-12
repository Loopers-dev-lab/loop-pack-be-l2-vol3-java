package com.loopers.interfaces.api.user;

import java.time.LocalDate;

public class UserV1Dto {

    public record RegisterRequest(
        String loginId,
        String password,
        String name,
        LocalDate birthDate,
        String email
    ) {}

    public record RegisterResponse(
        Long id,
        String loginId,
        String name,
        String email
    ) {}

    public record UserInfoResponse(
        String loginId,
        String name,
        LocalDate birthDate,
        String email
    ) {}

    public record UpdatePasswordRequest(
        String newPassword
    ) {}
}
