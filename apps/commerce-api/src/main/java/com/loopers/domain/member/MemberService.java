package com.loopers.domain.member;

import com.loopers.application.member.MemberInfo;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public Member register(String loginId, String rawPassword, String name, String birthday, String email) {
        Member.validateRawPassword(rawPassword, birthday);
        String encryptedPassword = passwordEncoder.encode(rawPassword);

        memberRepository.findByLoginId(loginId).ifPresent(m -> {
            throw new CoreException(ErrorType.CONFLICT, "이미 가입된 로그인 ID입니다.");
        });
        Member member = new Member(loginId, encryptedPassword, name, birthday, email);
        return memberRepository.save(member);
    }

    @Transactional(readOnly = true)
    public Member getMember(String loginId) {
        return memberRepository.findByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "회원을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public MemberInfo getMyInfo(String loginId, String rawPassword) {
        Member member = authenticate(loginId, rawPassword);
        return MemberInfo.fromWithMaskedName(member);
    }

    @Transactional
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
        Member member = getMember(loginId);
        if (!passwordEncoder.matches(rawPassword, member.getPassword())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호가 일치하지 않습니다.");
        }
        return member;
    }
}
