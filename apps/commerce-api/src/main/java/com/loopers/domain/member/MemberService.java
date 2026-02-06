package com.loopers.domain.member;

import com.loopers.application.member.dto.AddMemberReqDto;
import com.loopers.application.member.dto.FindMemberResDto;
import com.loopers.application.member.dto.PutMemberPasswordReqDto;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.support.validation.MemberValidatorUtil;
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
    public void addMember(AddMemberReqDto command) {
        // 중복 검증
        if (memberRepository.existsByLoginId(command.loginId())) {
            throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 로그인 ID입니다.");
        }
        if (memberRepository.existsByEmail(command.email())) {
            throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 이메일입니다.");
        }

        // 비밀번호 암호화 후 저장
        MemberModel member = MemberModel.signUp(
            command.loginId(),
            passwordEncoder.encode(command.password()),
            command.name(),
            command.birthDate(),
            command.email()
        );
        memberRepository.save(member);
    }

    @Transactional(readOnly = true)
    public FindMemberResDto findMember(String loginId, String password) {
        MemberModel member = memberRepository.findByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.UNAUTHORIZED, ErrorType.UNAUTHORIZED.getMessage()));

        if (!passwordEncoder.matches(password, member.getPassword())) {
            throw new CoreException(ErrorType.UNAUTHORIZED, ErrorType.UNAUTHORIZED.getMessage());
        }

        return FindMemberResDto.from(member);
    }

    @Transactional
    public void putPassword(PutMemberPasswordReqDto command) {
        MemberModel member = memberRepository.findByLoginId(command.loginId())
            .orElseThrow(() -> new CoreException(ErrorType.UNAUTHORIZED, ErrorType.UNAUTHORIZED.getMessage()));

        MemberValidatorUtil.validatePassword(command.newPassword(), member.getBirthDate());

        // 헤더 비밀번호로 인증
        if (!passwordEncoder.matches(command.loginPassword(), member.getPassword())) {
            throw new CoreException(ErrorType.UNAUTHORIZED, ErrorType.UNAUTHORIZED.getMessage());
        }

        // 현재 비밀번호 확인
        if (!passwordEncoder.matches(command.currentPassword(), member.getPassword())) {
            throw new CoreException(ErrorType.BAD_REQUEST, ErrorType.BAD_REQUEST.getMessage());
        }

        // 동일 비밀번호 검증
        MemberValidatorUtil.validatePasswordChange(command.currentPassword(), command.newPassword());

        // 비밀번호 변경
        member.changePassword(passwordEncoder.encode(command.newPassword()));
        memberRepository.save(member);
    }
}
