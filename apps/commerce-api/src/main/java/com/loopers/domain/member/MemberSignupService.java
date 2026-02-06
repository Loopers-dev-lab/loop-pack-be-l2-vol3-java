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
public class MemberSignupService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public MemberModel signup(String loginId, String rawPassword, String name,
                              LocalDate birthDate, String email) {
        LoginId loginIdVo = new LoginId(loginId);
        MemberName nameVo = new MemberName(name);
        Email emailVo = email != null ? new Email(email) : null;
        Password password = Password.of(rawPassword, birthDate);

        memberRepository.findByLoginId(loginId).ifPresent(m -> {
            throw new CoreException(ErrorType.DUPLICATE_LOGIN_ID);
        });

        String encodedPassword = passwordEncoder.encode(rawPassword);
        MemberModel member = new MemberModel(loginIdVo, encodedPassword, nameVo, birthDate, emailVo);
        return memberRepository.save(member);
    }
}
