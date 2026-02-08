package com.loopers.user.interfaces.controller.request;

import com.loopers.user.application.dto.in.UserSignUpInDto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * 회원가입 요청
 * - loginId: 로그인 ID
 * - password: 비밀번호
 * - name: 이름
 * - birthday: 생년월일
 * - email: 이메일
 */
public record UserSignUpRequest(
	@NotBlank(message = "로그인 ID는 필수입니다.")
	String loginId,

	@NotBlank(message = "비밀번호는 필수입니다.")
	String password,

	@NotBlank(message = "이름은 필수입니다.")
	String name,

	@NotNull(message = "생년월일은 필수입니다.")
	LocalDate birthday,

	@NotBlank(message = "이메일은 필수입니다.")
	String email
) {

	// 1. 회원가입 요청을 애플리케이션 입력 DTO로 변환
	public UserSignUpInDto toInDto() {
		return new UserSignUpInDto(loginId, password, name, birthday, email);
	}
}
