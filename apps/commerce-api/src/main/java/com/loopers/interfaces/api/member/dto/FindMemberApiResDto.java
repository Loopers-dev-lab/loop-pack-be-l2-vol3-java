package com.loopers.interfaces.api.member.dto;

import com.loopers.application.member.dto.FindMemberResDto;
import com.loopers.support.util.MaskingUtil;

import java.time.LocalDate;

public record FindMemberApiResDto(
        String loginId,
        String name,
        LocalDate birthDate,
        String email
) {
    public static FindMemberApiResDto from(FindMemberResDto resDto) {
        return new FindMemberApiResDto(
                resDto.loginId(),
                MaskingUtil.maskLast(resDto.name(), 1),
                resDto.birthDate(),
                resDto.email()
        );
    }
}
