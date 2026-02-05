package com.loopers.application.member;

import com.loopers.domain.member.Member;
import com.loopers.domain.member.MemberService;
import com.loopers.domain.member.SignupCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class MemberFacade {

    private final MemberService memberService;

    // 회원가입
    public MemberInfo signup(SignupCommand command) {
        Member member = memberService.signup(command); // Entity 받음
        return MemberInfo.from(member); // record로 반환
    }

    // 내 정보 가져오기
    public MyInfo getMyInfo(String loginId, String rawPassword) {
        Member member = memberService.authenticate(loginId, rawPassword);
        return MyInfo.from(member);
    }

    // 비밀번호 변경
    public void changePassword(String loginId, String headerPassword, String currentPassword, String newPassword) {
        Member member = memberService.authenticate(loginId, headerPassword);
        memberService.changePassword(member, currentPassword, newPassword);
    }
}
