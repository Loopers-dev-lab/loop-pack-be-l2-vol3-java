package com.loopers.application.member;

import com.loopers.domain.member.MemberAuthService;
import com.loopers.domain.member.MemberModel;
import com.loopers.domain.member.MemberPasswordService;
import com.loopers.domain.member.MemberSignupService;
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

    public MemberInfo getMyInfo(String loginId, String password) {
        MemberModel member = memberAuthService.authenticate(loginId, password);
        return MemberInfo.fromWithMaskedName(member);
    }

    public void changePassword(String loginId, String password,
                               String currentPassword, String newPassword) {
        MemberModel member = memberAuthService.authenticate(loginId, password);
        memberPasswordService.changePassword(member, currentPassword, newPassword);
    }
}
