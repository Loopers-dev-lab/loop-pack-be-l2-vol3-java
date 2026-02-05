package com.loopers.application.member;

import com.loopers.domain.member.Member;

import java.time.LocalDate;

public record MemberInfo(Long id, String loginId, String name, LocalDate birthday, String email) {
    public static MemberInfo from(Member member) {
        return new MemberInfo(
            member.getId(),
            member.getLoginId(),
            member.getName(),
            member.getBirthday(),
            member.getEmail()
        );
    }

    public MemberInfo withMaskedName() {
        String maskedName = name.substring(0, name.length() - 1) + "*";
        return new MemberInfo(id, loginId, maskedName, birthday, email);
    }
}