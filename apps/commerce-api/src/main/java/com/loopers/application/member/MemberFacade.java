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

    public MemberInfo register(String loginId, String rawPassword, String name,
                               LocalDate birthDate, String email) {
        Member member = memberService.register(loginId, rawPassword, name, birthDate, email);
        return MemberInfo.from(member);
    }
}
