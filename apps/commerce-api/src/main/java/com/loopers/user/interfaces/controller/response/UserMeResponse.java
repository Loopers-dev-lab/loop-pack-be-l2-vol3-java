package com.loopers.user.interfaces.controller.response;

import com.loopers.user.domain.model.User;

import java.time.LocalDate;

public record UserMeResponse(
	String loginId,
	String name,
	LocalDate birthday,
	String email
) {
	public static UserMeResponse from(User user) {
		return new UserMeResponse(
			user.getLoginId(),
			maskName(user.getName()),
			user.getBirthday(),
			user.getEmail()
		);
	}

	private static String maskName(String name) {
		if (name.length() <= 1) {
			return "*";
		}
		return name.substring(0, name.length() - 1) + "*";
	}
}
