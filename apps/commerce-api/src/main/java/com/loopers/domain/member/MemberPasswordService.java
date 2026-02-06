package com.loopers.domain.member;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class MemberPasswordService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void changePassword(MemberModel member, String currentPassword, String newRawPassword) {
        if (!member.matchesPassword(currentPassword, passwordEncoder)) {
            throw new CoreException(ErrorType.PASSWORD_MISMATCH);
        }

        Password newPassword = new Password(newRawPassword);

        if (member.matchesPassword(newRawPassword, passwordEncoder)) {
            throw new CoreException(ErrorType.PASSWORD_SAME_AS_OLD);
        }
        newPassword.validateAgainst(member.birthDate());

        member.changePassword(passwordEncoder.encode(newRawPassword));
        memberRepository.save(member);
    }
}
