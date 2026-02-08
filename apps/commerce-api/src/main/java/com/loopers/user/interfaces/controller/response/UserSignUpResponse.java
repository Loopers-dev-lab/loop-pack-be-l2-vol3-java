package com.loopers.user.interfaces.controller.response;

import com.loopers.user.application.dto.out.UserSignUpOutDto;

import java.time.LocalDate;

/**
 * 회원가입 응답
 * - id: 유저 ID
 * - loginId: 로그인 ID
 * - name: 이름
 * - birthday: 생년월일
 * - email: 이메일
 */
public record UserSignUpResponse(
	Long id,
	String loginId,
	String name,
	LocalDate birthday,
	String email
) {

	// 1. 회원가입 결과 DTO를 컨트롤러 응답 객체로 변환
	public static UserSignUpResponse from(UserSignUpOutDto outDto) {
		return new UserSignUpResponse(
			outDto.id(),
			outDto.loginId(),
			outDto.name(),
			outDto.birthday(),
			outDto.email()
		);
	}
}
