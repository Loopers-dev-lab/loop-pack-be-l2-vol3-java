package com.loopers.application.member.dto;

import com.loopers.domain.member.model.MemberCommand;

import java.time.LocalDate;

public record AddMemberReqDto(
        String loginId,
        String rawPassword,
        String name,
        LocalDate birthDate,
        String email
) {
    public MemberCommand.SignUp toCommand() {
        return new MemberCommand.SignUp(loginId, rawPassword, name, birthDate, email);
    }
}
