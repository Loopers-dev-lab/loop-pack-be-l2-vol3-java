package com.loopers.domain.member;

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
    public Member signUp(String loginId, String rawPassword, String name, String birthDate, String email) {
        if (memberRepository.existsByLoginId(loginId)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 사용 중인 로그인ID입니다.");
        }

        String encryptedPassword = passwordEncoder.encode(rawPassword);
        Member member = new Member(loginId, encryptedPassword, name, birthDate, email);
        return memberRepository.save(member);
    }
}
