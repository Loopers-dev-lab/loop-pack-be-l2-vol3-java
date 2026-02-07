package com.loopers.domain.member;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final MemberReader memberReader;
    private final PasswordHasher passwordHasher;

    @Transactional
    public MemberModel register(String memberId, String rawPassword, String email, String birthDate, String name, Gender gender) {
        if (memberReader.existsByMemberId(memberId)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 가입된 ID 입니다.");
        }

        MemberModel member = MemberModel.create(memberId, rawPassword, email, birthDate, name, gender, passwordHasher);
        return memberRepository.save(member);
    }

    @Transactional(readOnly = true)
    public MemberModel authenticate(String loginId, String loginPw) {
        MemberModel member = memberReader.getOrThrow(loginId);
        if (!member.verifyPassword(passwordHasher, loginPw)) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "비밀번호가 일치하지 않습니다.");
        }
        return member;
    }

    @Transactional
    public void changePassword(String loginId, String loginPw,
                               String currentPassword, String newPassword) {
        MemberModel member = memberReader.getOrThrow(loginId);
        if (!member.verifyPassword(passwordHasher, loginPw)) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "비밀번호가 일치하지 않습니다.");
        }
        member.changePassword(currentPassword, newPassword, passwordHasher);
        memberRepository.save(member);
    }
}
