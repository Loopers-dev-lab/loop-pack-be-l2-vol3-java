package com.loopers.application.member;

import com.loopers.domain.member.MemberModel;

import java.time.LocalDate;

public record MemberInfo(Long id, String loginId, String name, LocalDate birthDate, String email) {
    public static MemberInfo from(MemberModel model) {
        return new MemberInfo(
            model.getId(),
            model.getLoginId(),
            model.getName(),
            model.getBirthDate(),
            model.getEmail()
        );
    }
}
