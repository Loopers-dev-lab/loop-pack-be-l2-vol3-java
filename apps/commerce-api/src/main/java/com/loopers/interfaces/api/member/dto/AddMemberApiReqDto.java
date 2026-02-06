package com.loopers.interfaces.api.member.dto;

import com.loopers.application.member.MemberCommand;
import com.loopers.application.member.dto.AddMemberReqDto;

import java.time.LocalDate;

public record AddMemberApiReqDto(
        String loginId,
        String password,
        String name,
        LocalDate birthDate,
        String email
) {
    public AddMemberReqDto toCommand() {
        return new AddMemberReqDto(loginId, password, name, birthDate, email);
    }
}
