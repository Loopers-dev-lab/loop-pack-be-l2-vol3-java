package com.loopers.domain.member.model;

import java.time.LocalDate;

public final class MemberCommand {

    private MemberCommand() {}

    public record SignUp(
        String loginId,
        String rawPassword,
        String name,
        LocalDate birthDate,
        String email
    ) {}

    public record ChangePassword(
        String loginId,
        String loginPassword,
        String currentPassword,
        String newPassword
    ) {}
}
