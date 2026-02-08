package com.loopers.interfaces.api.member.dto;

import com.loopers.domain.member.MemberModel;

import java.time.LocalDate;

public record FindMemberApiResDto(
        String loginId,
        String name,
        LocalDate birthDate,
        String email
) {
    public static FindMemberApiResDto from(MemberModel model) {
        return new FindMemberApiResDto(
                model.getLoginId().value(),
                model.getName().masked(),
                model.getBirthDate().value(),
                model.getEmail().value()
        );
    }
}
