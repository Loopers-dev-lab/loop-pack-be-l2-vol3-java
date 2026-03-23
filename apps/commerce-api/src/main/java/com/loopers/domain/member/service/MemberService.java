package com.loopers.domain.member.service;

import com.loopers.domain.member.model.Member;
import com.loopers.domain.member.model.MemberCommand;
import com.loopers.domain.member.repository.MemberRepository;
import com.loopers.domain.member.vo.*;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncryptor passwordEncryptor;

    public void addMember(MemberCommand.SignUp command) {
        if (memberRepository.existsByLoginId(new LoginId(command.loginId()))) {
            throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 로그인 ID입니다.");
        }
        if (memberRepository.existsByEmail(new Email(command.email()))) {
            throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 이메일입니다.");
        }

        Member member = Member.signUp(command, passwordEncryptor);
        memberRepository.save(member);
    }

    public Member findMember(String loginId, String rawPassword) {
        Member member = memberRepository.findByLoginId(new LoginId(loginId))
            .orElseThrow(() -> new CoreException(ErrorType.UNAUTHORIZED, ErrorType.UNAUTHORIZED.getMessage()));

        if (!member.getPassword().matches(rawPassword, passwordEncryptor)) {
            throw new CoreException(ErrorType.UNAUTHORIZED, ErrorType.UNAUTHORIZED.getMessage());
        }

        return member;
    }

    public void changePassword(MemberCommand.ChangePassword command) {
        Member member = findMember(command.loginId(), command.loginPassword());

        // 헤더 인증 비밀번호(loginPassword)와 바디의 현재 비밀번호(currentPassword)가 동일한지 확인
        if (!command.loginPassword().equals(command.currentPassword())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "인증 비밀번호와 현재 비밀번호가 일치하지 않습니다.");
        }

        if (command.currentPassword().equals(command.newPassword())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "새 비밀번호는 현재 비밀번호와 달라야 합니다.");
        }

        Password newPassword = Password.create(command.newPassword(), member.getBirthDate().toFormattedString(), passwordEncryptor);
        memberRepository.updatePassword(member.getLoginId(), newPassword);
    }

}
