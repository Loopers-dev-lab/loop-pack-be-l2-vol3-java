package com.loopers.application.member.dto;

import java.time.LocalDate;

public record AddMemberReqDto(
        String loginId,
        String password,
        String name,
        LocalDate birthDate,
        String email
) {
}
