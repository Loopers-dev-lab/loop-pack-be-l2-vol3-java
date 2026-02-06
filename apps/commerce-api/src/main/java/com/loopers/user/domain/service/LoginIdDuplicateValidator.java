package com.loopers.user.domain.service;

import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;

import java.util.function.Predicate;

public class LoginIdDuplicateValidator {

	private final Predicate<String> existsByLoginId;

	public LoginIdDuplicateValidator(Predicate<String> existsByLoginId) {
		this.existsByLoginId = existsByLoginId;
	}

	public void validate(String loginId) {
		if (loginId == null || loginId.isBlank()) {
			throw new CoreException(ErrorType.INVALID_LOGIN_ID_FORMAT);
		}
		if (existsByLoginId.test(loginId)) {
			throw new CoreException(ErrorType.USER_ALREADY_EXISTS);
		}
	}
}
