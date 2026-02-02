package com.loopers.application.member;

import com.loopers.domain.member.Member;

public record MemberInfo(Long id, String loginId, String name, String email) {
    public static MemberInfo from(Member member) {
        return new MemberInfo(
            member.getId(),
            member.getLoginId(),
            member.getName(),
            member.getEmail()
        );
    }
}