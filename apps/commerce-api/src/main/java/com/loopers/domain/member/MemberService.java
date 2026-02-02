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
}