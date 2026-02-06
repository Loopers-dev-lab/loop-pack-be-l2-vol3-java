package com.loopers.application.member;

import java.time.LocalDate;

public class MemberCommand {

    public record SignUp(
        String loginId,
        String password,
        String name,
        LocalDate birthDate,
        String email
    ) {}

    public record ChangePassword(
        String loginId,
        String headerPassword,
        String currentPassword,
        String newPassword
    ) {}
}
