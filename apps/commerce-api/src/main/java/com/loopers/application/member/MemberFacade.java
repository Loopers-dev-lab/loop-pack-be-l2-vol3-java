package com.loopers.application.member;

import com.loopers.domain.member.MemberModel;
import com.loopers.domain.member.MemberService;
import com.loopers.interfaces.api.member.MemberV1Dto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class MemberFacade {

  private final MemberService memberService;

  public MemberInfo signupMember(MemberV1Dto.SignUpRequest request) {
    // 1. Request → MemberModel로 변환
    MemberModel memberModel = new MemberModel(
        request.loginId(),
        request.password(),
        request.name(),
        request.birthDate(),
        request.email()
    );

    // 2. Service 호출 (저장 + 중복 체크)
    MemberModel saved = memberService.saveMember(memberModel);

    // 3. MemberModel → MemberInfo로 변환해서 반환
    return MemberInfo.from(saved);
  }


  public MemberInfo getMyInfo(String loginId, String password) {
    MemberModel member = memberService.authenticate(loginId, password);
    return MemberInfo.from(member);
  }


  public void changePassword(String loginId, String password, String prevPassword, String newPassword) {
    // 헤더 인증
    memberService.authenticate(loginId, password);

    MemberModel memberModel = new MemberModel(loginId, prevPassword);  // raw prevPassword

    // Service 호출
    memberService.changePassword(memberModel, newPassword);

  }
}
