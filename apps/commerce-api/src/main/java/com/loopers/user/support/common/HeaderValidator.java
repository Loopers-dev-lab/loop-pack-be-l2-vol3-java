package com.loopers.user.support.common;

import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;

/**
 * 인증 헤더 검증 유틸리티
 * 1. 로그인 ID/비밀번호 헤더 검증
 */
public final class HeaderValidator {

	private HeaderValidator() {
	}

	// 1. 로그인 ID/비밀번호 헤더 검증
	public static void validate(String loginId, String password) {

		// 로그인 ID 누락 검증
		if (loginId == null || loginId.isBlank()) {
			throw new CoreException(ErrorType.UNAUTHORIZED);
		}

		// 비밀번호 누락 검증
		if (password == null || password.isBlank()) {
			throw new CoreException(ErrorType.UNAUTHORIZED);
		}
	}
}
