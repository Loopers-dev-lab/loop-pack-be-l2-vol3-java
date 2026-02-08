package com.loopers.user.application.dto.in;

import java.time.LocalDate;

/**
 * 회원가입 입력 DTO
 * - loginId: 로그인 ID
 * - password: 비밀번호
 * - name: 이름
 * - birthday: 생년월일
 * - email: 이메일
 */
public record UserSignUpInDto(
	String loginId,
	String password,
	String name,
	LocalDate birthday,
	String email
) {
}
