package com.loopers.application.member;

import com.loopers.domain.member.Member;

/*
    # Info의 역할
    비즈니스 로직 결과를 전달하는 용도.
 */
public record MemberInfo(
        Long id,
        String loginId,
        String name,
        String email,
        String birthDate) {
    public static MemberInfo from(Member member) {
        return new MemberInfo(
                member.getId(),
                member.getLoginId(),
                member.getName(),
                member.getEmail(),
                member.getBirthDate());
    }
}
