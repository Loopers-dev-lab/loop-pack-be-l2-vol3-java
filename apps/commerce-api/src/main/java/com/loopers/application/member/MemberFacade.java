package com.loopers.application.member;

import com.loopers.application.member.dto.AddMemberReqDto;
import com.loopers.application.member.dto.FindMemberResDto;
import com.loopers.application.member.dto.PutMemberPasswordReqDto;
import com.loopers.domain.member.MemberService;
import com.loopers.interfaces.api.member.dto.FindMemberApiResDto;
import com.loopers.support.validation.MemberValidatorUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class MemberFacade {
    private final MemberService service;

    public void addMember(AddMemberReqDto command) {
        // 유효성 검증
        MemberValidatorUtil.validateLoginId(command.loginId());
        MemberValidatorUtil.validatePassword(command.password(), command.birthDate());
        MemberValidatorUtil.validateName(command.name());
        MemberValidatorUtil.validateBirthDate(command.birthDate());
        MemberValidatorUtil.validateEmail(command.email());

        service.addMember(command);
    }

    public FindMemberApiResDto findMember(String loginId, String password) {
        FindMemberResDto memberResDto = service.findMember(loginId, password);
        return FindMemberApiResDto.from(memberResDto);
    }

    public void putPassword(PutMemberPasswordReqDto command) {
        // 새 비밀번호 검증
        MemberValidatorUtil.validatePasswordChange(command.currentPassword(), command.newPassword());
        service.putPassword(command);
    }
}
