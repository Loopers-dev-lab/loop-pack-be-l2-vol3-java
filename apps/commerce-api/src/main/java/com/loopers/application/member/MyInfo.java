package com.loopers.application.member;

import com.loopers.domain.member.Member;
import com.loopers.domain.member.MemberName;

public record MyInfo(
    String loginId,
    String name,
    String email,
    String birthDate
) {
    public static MyInfo from(Member member) {
        MemberName memberName = new MemberName(member.getName());
        return new MyInfo(
            member.getLoginId(),
            memberName.masked(),
            member.getEmail(),
            member.getBirthDate()
        );
    }
}
