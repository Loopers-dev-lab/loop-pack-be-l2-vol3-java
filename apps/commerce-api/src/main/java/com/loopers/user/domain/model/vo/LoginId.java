package com.loopers.user.domain.model.vo;


import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;

import java.util.Locale;
import java.util.regex.Pattern;


/**
 * 로그인 ID 값 객체
 * - value: 정규화된 로그인 ID 값
 */

public record LoginId(String value) {

	private static final int MIN_LENGTH = 4;    // 로그인 ID 최소 길이
	private static final int MAX_LENGTH = 20;   // 로그인 ID 최대 길이
	private static final Pattern PATTERN = Pattern.compile("^[a-zA-Z0-9]+$"); // 영문 대소문자 및 숫자만 허용


	// 생성자
	public LoginId {

		// 값 정규화
		String normalized = normalize(value);

		// 값 검증
		validate(normalized);

		// 필드 할당
		value = normalized;
	}


	/**
	 * 도메인 로직
	 * 1. 로그인 ID 생성
	 * 2. 로그인 ID 정규화
	 */

	// 1. 로그인 ID 생성
	public static LoginId create(String rawLoginId) {
		return new LoginId(rawLoginId);
	}


	// 2. 로그인 ID 정규화
	public static String normalize(String loginId) {
		if (loginId == null) {
			return null;
		}
		String normalized = loginId.trim().toLowerCase(Locale.ROOT);
		return normalized.isBlank() ? null : normalized;
	}


	/**
	 * private 메서드
	 * 1. 로그인 ID 형식 검증
	 */

	// 1. 로그인 ID 형식 검증
	private static void validate(String loginId) {

		if (loginId == null ||
			loginId.length() < MIN_LENGTH ||
			loginId.length() > MAX_LENGTH ||
			!PATTERN.matcher(loginId).matches()
		) {
			throw new CoreException(ErrorType.INVALID_LOGIN_ID_FORMAT);
		}
	}

}
