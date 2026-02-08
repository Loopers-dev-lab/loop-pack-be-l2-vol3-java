package com.loopers.user.interfaces.controller.response;

import com.loopers.user.application.dto.out.UserMeOutDto;

import java.time.LocalDate;

/**
 * 내 정보 조회 응답
 * - loginId: 로그인 ID
 * - name: 마스킹된 이름
 * - birthday: 생년월일
 * - email: 이메일
 */
public record UserMeResponse(
	String loginId,
	String name,
	LocalDate birthday,
	String email
) {

	// 1. 내 정보 조회 결과 DTO를 컨트롤러 응답 객체로 변환
	public static UserMeResponse from(UserMeOutDto outDto) {
		return new UserMeResponse(
			outDto.loginId(),
			maskName(outDto.name()),
			outDto.birthday(),
			outDto.email()
		);
	}

	// 2. 이름 마지막 글자를 마스킹 처리
	private static String maskName(String name) {
		if (name == null || name.length() <= 1) {
			return "*";
		}
		return name.substring(0, name.length() - 1) + "*";
	}
}
