package com.loopers.domain.user.dto;

public class UserCommand {

    public record Signup(
        String loginId,
        String rawPassword,
        String name,
        String birthDate,
        String email
    ) {
    }

    public record ChangePassword(
        String rawCurrentPassword,
        String rawNewPassword
    ) {
    }
}
