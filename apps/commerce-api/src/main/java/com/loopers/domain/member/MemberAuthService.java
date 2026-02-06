package com.loopers.domain.member;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class MemberAuthService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public MemberModel authenticate(String loginId, String password) {
        MemberModel member = memberRepository.findByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.MEMBER_NOT_FOUND));

        if (!member.matchesPassword(password, passwordEncoder)) {
            throw new CoreException(ErrorType.AUTHENTICATION_FAILED);
        }
        return member;
    }
}
