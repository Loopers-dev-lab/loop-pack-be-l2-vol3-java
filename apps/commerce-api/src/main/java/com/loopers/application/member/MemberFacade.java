package com.loopers.application.member;

import com.loopers.application.member.dto.AddMemberReqDto;
import com.loopers.application.member.dto.PutMemberPasswordReqDto;
import com.loopers.domain.member.MemberModel;
import com.loopers.domain.member.MemberService;
import com.loopers.domain.member.vo.BirthDate;
import com.loopers.domain.member.vo.Email;
import com.loopers.domain.member.vo.LoginId;
import com.loopers.domain.member.vo.MemberName;
import com.loopers.domain.member.vo.Password;
import com.loopers.interfaces.api.member.dto.FindMemberApiResDto;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class MemberFacade {
    private final MemberService service;
    private final PasswordEncoder passwordEncoder;

    public void addMember(AddMemberReqDto command) {
        LoginId loginId = new LoginId(command.loginId());
        BirthDate birthDate = new BirthDate(command.birthDate());
        Password.validateRawPassword(command.password(), birthDate.toFormattedString());
        Password password = new Password(passwordEncoder.encode(command.password()));
        MemberName name = new MemberName(command.name());
        Email email = new Email(command.email());

        service.addMember(loginId, password, name, birthDate, email);
    }

    public FindMemberApiResDto findMember(String loginId, String password) {
        MemberModel member = service.findMember(loginId, password);
        return FindMemberApiResDto.from(member);
    }

    public void putPassword(PutMemberPasswordReqDto command) {
        // 1. 헤더 인증
        MemberModel member = service.findMember(command.loginId(), command.loginPassword());

        // 2. 현재 비밀번호 확인
        service.verifyPassword(member, command.currentPassword());

        // 3. 동일 비밀번호 체크
        if (command.currentPassword().equals(command.newPassword())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "새 비밀번호는 현재 비밀번호와 달라야 합니다.");
        }

        // 4. 새 비밀번호 규칙 검증
        Password.validateRawPassword(command.newPassword(), member.getBirthDate().toFormattedString());

        // 5. 변경
        Password newPassword = new Password(passwordEncoder.encode(command.newPassword()));
        service.updatePassword(command.loginId(), newPassword);
    }
}
