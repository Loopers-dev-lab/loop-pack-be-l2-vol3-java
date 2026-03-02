package com.loopers.interfaces.api.member.dto;

import com.loopers.application.member.dto.FindMemberResDto;

import java.time.LocalDate;

public record FindMemberApiResDto(
        String loginId,
        String name,
        LocalDate birthDate,
        String email
) {
    public static FindMemberApiResDto from(FindMemberResDto dto) {
        return new FindMemberApiResDto(
                dto.loginId(),
                dto.name(),
                dto.birthDate(),
                dto.email()
        );
    }
}
