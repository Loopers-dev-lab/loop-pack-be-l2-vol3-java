package com.loopers.user.domain.service;

import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;

import java.util.Locale;
import java.util.function.Predicate;

public class LoginIdDuplicateValidator {

	private final Predicate<String> existsByLoginId;

	/**
	 * 로그인 ID 중복 검증기
	 * 1. 로그인 ID 중복 검증
	 */

	public LoginIdDuplicateValidator(Predicate<String> existsByLoginId) {
		this.existsByLoginId = existsByLoginId;
	}

	// 1. 로그인 ID 중복 검증
	public void validate(String loginId) {
		String normalizedLoginId = normalize(loginId);

		// 로그인 ID 필수값 검증
		if (normalizedLoginId == null || normalizedLoginId.isBlank()) {
			throw new CoreException(ErrorType.INVALID_LOGIN_ID_FORMAT);
		}

		// 동일 로그인 ID가 이미 존재하면 예외 반환
		if (existsByLoginId.test(normalizedLoginId)) {
			throw new CoreException(ErrorType.USER_ALREADY_EXISTS);
		}
	}

	private String normalize(String loginId) {
		if (loginId == null) {
			return null;
		}
		return loginId.trim().toLowerCase(Locale.ROOT);
	}
}
