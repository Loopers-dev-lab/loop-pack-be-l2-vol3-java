package com.loopers.application.member;

import com.loopers.domain.member.MemberModel;

import java.time.LocalDate;

public record MemberInfo(String loginId, String name, LocalDate birthDate, String email) {

    public static MemberInfo from(MemberModel model) {
        return new MemberInfo(
            model.loginId().value(),
            model.name().value(),
            model.birthDate(),
            model.email() != null ? model.email().value() : null
        );
    }

    public static MemberInfo fromWithMaskedName(MemberModel model) {
        return new MemberInfo(
            model.loginId().value(),
            model.name().masked(),
            model.birthDate(),
            model.email() != null ? model.email().value() : null
        );
    }
}
