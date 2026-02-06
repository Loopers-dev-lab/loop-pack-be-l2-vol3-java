package com.loopers.application.member;

import com.loopers.domain.member.MemberModel;
import com.loopers.support.util.MaskingUtil;

import java.time.LocalDate;

public record MemberInfo(
    Long id,
    String loginId,
    String name,
    LocalDate birthDate,
    String email
) {
    public static MemberInfo from(MemberModel model) {
        return new MemberInfo(
            model.getId(),
            model.getLoginId(),
            MaskingUtil.maskLast(model.getName(), 1),
            model.getBirthDate(),
            model.getEmail()
        );
    }
}
