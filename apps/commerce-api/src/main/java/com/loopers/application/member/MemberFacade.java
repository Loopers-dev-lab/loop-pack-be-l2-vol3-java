package com.loopers.application.member;

import com.loopers.domain.member.Member;
import com.loopers.domain.member.MemberService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class MemberFacade {

    private final MemberService memberService;
    private final PasswordEncoder passwordEncoder;

    public MemberInfo register(String loginId, String rawPassword, String name, String birthday, String email) {
        Member.validateRawPassword(rawPassword, birthday);
        String encryptedPassword = passwordEncoder.encode(rawPassword);
        Member member = memberService.register(loginId, encryptedPassword, name, birthday, email);
        return MemberInfo.from(member);
    }

    public MemberInfo getMyInfo(String loginId, String rawPassword) {
        Member member = authenticate(loginId, rawPassword);
        return MemberInfo.fromWithMaskedName(member);
    }

    public void changePassword(String loginId, String rawCurrentPassword, String rawNewPassword) {
        Member member = authenticate(loginId, rawCurrentPassword);

        if (passwordEncoder.matches(rawNewPassword, member.getPassword())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "새 비밀번호는 기존 비밀번호와 달라야 합니다.");
        }

        Member.validateRawPassword(rawNewPassword, member.getBirthday());

        String encryptedNewPassword = passwordEncoder.encode(rawNewPassword);
        member.changePassword(encryptedNewPassword);
    }

    private Member authenticate(String loginId, String rawPassword) {
        Member member = memberService.getMember(loginId);
        if (!passwordEncoder.matches(rawPassword, member.getPassword())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호가 일치하지 않습니다.");
        }
        return member;
    }
}
