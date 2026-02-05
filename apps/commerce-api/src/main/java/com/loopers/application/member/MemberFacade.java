package com.loopers.application.member;

import com.loopers.domain.member.Member;
import com.loopers.domain.member.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@RequiredArgsConstructor
@Component
public class MemberFacade {

    private final MemberService memberService;

    public MemberInfo signUp(String loginId, String password, String name, LocalDate birthday, String email) {
        Member member = memberService.signUp(loginId, password, name, birthday, email);
        return MemberInfo.from(member);
    }

    public MemberInfo getMyInfo(String loginId, String password) {
        Member member = memberService.authenticate(loginId, password);
        return MemberInfo.from(member);
    }

    public void updatePassword(String loginId, String currentPassword, String newPassword) {
        memberService.updatePassword(loginId, currentPassword, newPassword);
    }
}