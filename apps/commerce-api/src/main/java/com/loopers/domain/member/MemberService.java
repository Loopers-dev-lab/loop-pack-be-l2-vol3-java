package com.loopers.domain.member;

import com.loopers.domain.member.vo.BirthDate;
import com.loopers.domain.member.vo.Email;
import com.loopers.domain.member.vo.LoginId;
import com.loopers.domain.member.vo.MemberName;
import com.loopers.domain.member.vo.Password;
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
    public void addMember(LoginId loginId, Password password, MemberName name, BirthDate birthDate, Email email) {
        if (memberRepository.existsByLoginId(loginId.value())) {
            throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 로그인 ID입니다.");
        }
        if (memberRepository.existsByEmail(email.value())) {
            throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 이메일입니다.");
        }

        MemberModel member = MemberModel.signUp(loginId, password, name, birthDate, email);
        memberRepository.save(member);
    }

    @Transactional(readOnly = true)
    public MemberModel findMember(String loginId, String rawPassword) {
        MemberModel member = memberRepository.findByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.UNAUTHORIZED, ErrorType.UNAUTHORIZED.getMessage()));

        if (!passwordEncoder.matches(rawPassword, member.getPassword().value())) {
            throw new CoreException(ErrorType.UNAUTHORIZED, ErrorType.UNAUTHORIZED.getMessage());
        }

        return member;
    }

    public void verifyPassword(MemberModel member, String rawPassword) {
        if (!passwordEncoder.matches(rawPassword, member.getPassword().value())) {
            throw new CoreException(ErrorType.BAD_REQUEST, ErrorType.BAD_REQUEST.getMessage());
        }
    }

    @Transactional
    public void updatePassword(String loginId, Password newPassword) {
        memberRepository.updatePassword(loginId, newPassword.value());
    }
}
