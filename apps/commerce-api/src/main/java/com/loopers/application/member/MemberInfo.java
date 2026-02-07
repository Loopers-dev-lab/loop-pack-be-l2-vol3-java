package com.loopers.application.member;

import com.loopers.domain.member.Member;

public record MemberInfo(String loginId, String name, String birthday, String email) {

    public static MemberInfo from(Member member) {
        return new MemberInfo(
            member.getLoginId(),
            member.getName(),
            member.getBirthday(),
            member.getEmail()
        );
    }

    public static MemberInfo fromWithMaskedName(Member member) {
        return new MemberInfo(
            member.getLoginId(),
            member.getMaskedName(),
            member.getBirthday(),
            member.getEmail()
        );
    }
}
