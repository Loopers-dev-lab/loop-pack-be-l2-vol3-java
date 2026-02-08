package com.loopers.user.application.dto.out;

import com.loopers.user.domain.model.User;

import java.time.LocalDate;

/**
 * 회원가입 결과 DTO
 * - id: 유저 ID
 * - loginId: 로그인 ID
 * - name: 이름
 * - birthday: 생년월일
 * - email: 이메일
 */
public record UserSignUpOutDto(
	Long id,
	String loginId,
	String name,
	LocalDate birthday,
	String email
) {

	// 1. 유저 도메인 객체를 회원가입 결과 DTO로 변환
	public static UserSignUpOutDto from(User user) {
		return new UserSignUpOutDto(
			user.getId(),
			user.getLoginId(),
			user.getName(),
			user.getBirthday(),
			user.getEmail()
		);
	}
}
