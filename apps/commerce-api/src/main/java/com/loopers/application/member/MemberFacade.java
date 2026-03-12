package com.loopers.application.member;

import com.loopers.domain.member.MemberModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@RequiredArgsConstructor
@Component
public class MemberFacade {

    private final MemberSignupService memberSignupService;
    private final MemberAuthService memberAuthService;
    private final MemberPasswordService memberPasswordService;

    public MemberInfo signup(String loginId, String password, String name,
                             LocalDate birthDate, String email) {
        MemberModel member = memberSignupService.signup(loginId, password, name, birthDate, email);
        return MemberInfo.from(member);
    }

    public MemberModel authenticate(String loginId, String password) {
        return memberAuthService.authenticate(loginId, password);
    }

    public MemberInfo getMyInfo(MemberModel member) {
        return MemberInfo.fromWithMaskedName(member);
    }

    public void changePassword(MemberModel member, String currentPassword, String newPassword) {
        memberPasswordService.changePassword(member, currentPassword, newPassword);
    }
}
