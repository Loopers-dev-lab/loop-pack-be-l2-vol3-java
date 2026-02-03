package com.loopers.application.member;

import com.loopers.domain.member.MemberModel;
import com.loopers.domain.member.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@RequiredArgsConstructor
@Component
public class MemberFacade {
    private final MemberService memberService;

    public MemberInfo register(String loginId, String password, String name, LocalDate birthDate, String email) {
        MemberModel member = memberService.register(loginId, password, name, birthDate, email);
        return MemberInfo.from(member);
    }

    public MemberInfo getMyInfo(String loginId, String password) {
        MemberModel member = memberService.authenticate(loginId, password);
        return MemberInfo.fromWithMaskedName(member);
    }

    public void changePassword(String loginId, String currentPassword, String newPassword) {
        memberService.changePassword(loginId, currentPassword, newPassword);
    }
}
