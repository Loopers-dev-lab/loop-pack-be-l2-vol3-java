package com.loopers.application.member;

import com.loopers.domain.member.MemberModel;
import com.loopers.domain.member.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@RequiredArgsConstructor
@Component
public class MemberFacade {

	private final MemberService memberService;

	public void register(String loginId, String password, String name, LocalDate birthDate, String email) {
		memberService.register(loginId, password, name, birthDate, email);
	}

	public MemberInfo getMyInfo(String loginId, String password) {
		MemberModel member = memberService.getMyInfo(loginId, password);
		return MemberInfo.from(member);
	}
}
