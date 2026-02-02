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
            maskLastChar(member.getName()),
            member.getBirthday(),
            member.getEmail()
        );
    }

    private static String maskLastChar(String name) {
        if (name == null || name.length() <= 1) {
            return "*";
        }
        return name.substring(0, name.length() - 1) + "*";
    }
}
