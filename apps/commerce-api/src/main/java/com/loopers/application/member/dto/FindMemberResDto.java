package com.loopers.application.member.dto;

import com.loopers.domain.member.model.Member;

import java.time.LocalDate;

public record FindMemberResDto(
        String loginId,
        String name,
        LocalDate birthDate,
        String email
) {
    public static FindMemberResDto from(Member model) {
        return new FindMemberResDto(
                model.getLoginId().value(),
                model.getName().masked(),
                model.getBirthDate().value(),
                model.getEmail().value()
        );
    }
}
