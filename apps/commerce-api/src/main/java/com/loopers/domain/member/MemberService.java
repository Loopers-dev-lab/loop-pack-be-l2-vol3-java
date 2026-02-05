package com.loopers.domain.member;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@RequiredArgsConstructor
@Component
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public Member authenticate(String loginId, String rawPassword) {
        Member member = memberRepository.findByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.UNAUTHORIZED));

        if (!passwordEncoder.matches(rawPassword, member.getPassword())) {
            throw new CoreException(ErrorType.UNAUTHORIZED);
        }

        return member;
    }

    @Transactional
    public Member signUp(String loginId, String password, String name, LocalDate birthday, String email) {
        if (memberRepository.existsByLoginId(loginId)) {
            throw new CoreException(ErrorType.CONFLICT);
        }
        if (memberRepository.existsByEmail(email)) {
            throw new CoreException(ErrorType.CONFLICT);
        }

        Member member = new Member(loginId, password, name, birthday, email);
        member.encryptPassword(passwordEncoder.encode(password));
        return memberRepository.save(member);
    }

    @Transactional
    public void updatePassword(String loginId, String currentPassword, String newPassword) {
        Member member = memberRepository.findByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.UNAUTHORIZED));

        if (!passwordEncoder.matches(currentPassword, member.getPassword())) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "현재 비밀번호가 일치하지 않습니다.");
        }
        if (passwordEncoder.matches(newPassword, member.getPassword())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "새 비밀번호는 현재 비밀번호와 달라야 합니다.");
        }

        String encodedNewPassword = passwordEncoder.encode(newPassword);
        member.changePassword(newPassword, encodedNewPassword);
        memberRepository.updatePassword(loginId, member.getPassword());
    }
}