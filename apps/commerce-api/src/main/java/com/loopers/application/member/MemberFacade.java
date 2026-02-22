package com.loopers.application.member;

import com.loopers.domain.member.Member;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MemberFacade {

    private final MemberAppService memberAppService;

    public Member signup(MemberAppService.SignupCommand command) {
        return memberAppService.signup(command);
    }

    public Member getByMemberId(String memberIdValue) {
        return memberAppService.getByMemberId(memberIdValue);
    }

    public void changePassword(String memberIdValue, String currentPassword, String newPassword) {
        memberAppService.changePassword(memberIdValue, currentPassword, newPassword);
    }
}
