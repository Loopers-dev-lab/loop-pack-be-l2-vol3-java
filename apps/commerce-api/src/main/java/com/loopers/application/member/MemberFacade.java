package com.loopers.application.member;

import com.loopers.domain.member.Member;
import com.loopers.domain.member.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class MemberFacade {

    private final MemberService memberService;

    @Transactional
    public Member signup(MemberService.SignupCommand command) {
        return memberService.signup(command);
    }

    @Transactional
    public void changePassword(Member member, String currentPassword, String newPassword) {
        memberService.changePassword(member, currentPassword, newPassword);
    }
}
