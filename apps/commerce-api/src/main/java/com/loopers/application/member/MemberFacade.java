package com.loopers.application.member;

import com.loopers.application.member.dto.AddMemberReqDto;
import com.loopers.application.member.dto.FindMemberResDto;
import com.loopers.application.member.dto.PutMemberPasswordReqDto;
import com.loopers.domain.member.model.Member;
import com.loopers.domain.member.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
@Transactional(readOnly = true)
public class MemberFacade {
    private final MemberService service;

    @Transactional(rollbackFor = {Exception.class})
    public void addMember(AddMemberReqDto dto) {
        service.addMember(dto.toCommand());
    }

    public FindMemberResDto findMember(String loginId, String password) {
        Member member = service.findMember(loginId, password);
        return FindMemberResDto.from(member);
    }

    @Transactional(rollbackFor = {Exception.class})
    public void putPassword(PutMemberPasswordReqDto dto) {
        service.changePassword(dto.toCommand());
    }
}
