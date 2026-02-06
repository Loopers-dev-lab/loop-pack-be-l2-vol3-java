package com.loopers.application.member.dto;

import com.loopers.domain.member.MemberModel;

import java.time.LocalDate;

public record FindMemberResDto(
        String loginId,
        String name,
        LocalDate birthDate,
        String email
) {
    public static FindMemberResDto from(MemberModel model) {
        return new FindMemberResDto(
                model.getLoginId(),
                model.getName(),
                model.getBirthDate(),
                model.getEmail()
        );
    }
}
