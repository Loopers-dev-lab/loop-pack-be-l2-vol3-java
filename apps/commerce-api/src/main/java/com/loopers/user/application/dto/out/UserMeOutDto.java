package com.loopers.user.application.dto.out;

import com.loopers.user.domain.model.User;

import java.time.LocalDate;

/**
 * 내 정보 조회 결과 DTO
 * - loginId: 로그인 ID
 * - name: 이름
 * - birthday: 생년월일
 * - email: 이메일
 */
public record UserMeOutDto(
	String loginId,
	String name,
	LocalDate birthday,
	String email
) {

	// 1. 유저 도메인 객체를 내 정보 조회 결과 DTO로 변환
	public static UserMeOutDto from(User user) {
		return new UserMeOutDto(
			user.getLoginId(),
			user.getName(),
			user.getBirthday(),
			user.getEmail()
		);
	}
}
