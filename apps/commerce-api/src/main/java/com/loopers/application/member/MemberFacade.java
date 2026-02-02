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
}