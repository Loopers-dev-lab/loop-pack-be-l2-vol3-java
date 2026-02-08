package com.loopers.user.interfaces.controller.request;

import com.loopers.user.application.dto.in.UserChangePasswordInDto;
import jakarta.validation.constraints.NotBlank;

/**
 * 비밀번호 변경 요청
 * - currentPassword: 현재 비밀번호
 * - newPassword: 새 비밀번호
 */
public record UserChangePasswordRequest(
	@NotBlank(message = "현재 비밀번호는 필수입니다.")
	String currentPassword,

	@NotBlank(message = "새 비밀번호는 필수입니다.")
	String newPassword
) {

	// 1. 비밀번호 변경 요청을 애플리케이션 입력 DTO로 변환
	public UserChangePasswordInDto toInDto() {
		return new UserChangePasswordInDto(currentPassword, newPassword);
	}
}
