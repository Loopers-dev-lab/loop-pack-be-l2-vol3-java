package com.loopers.user.domain.model;

import com.loopers.user.domain.model.vo.Password;
import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;

import java.time.LocalDate;
import java.util.regex.Pattern;

public class User {

	private static final int LOGIN_ID_MAX_LENGTH = 20;
	private static final int NAME_MAX_LENGTH = 100;
	private static final int EMAIL_MAX_LENGTH = 254;
	private static final LocalDate MIN_BIRTHDAY = LocalDate.of(1900, 1, 1);

	private static final Pattern LOGIN_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9]+$");
	private static final Pattern NAME_PATTERN = Pattern.compile("^[가-힣a-zA-Z]+$");
	private static final Pattern EMAIL_PATTERN = Pattern.compile("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");
	private static final Pattern EMAIL_DOMAIN_INVALID_PATTERN = Pattern.compile("(\\.\\.|^\\.|\\.$)");

	private Long id;
	private final String loginId;
	private final Password password;
	private final String name;
	private final LocalDate birthday;
	private final String email;

	private User(Long id, String loginId, Password password, String name, LocalDate birthday, String email) {
		this.id = id;
		this.loginId = loginId;
		this.password = password;
		this.name = name;
		this.birthday = birthday;
		this.email = email;
	}

	public static User create(String loginId, String rawPassword, String name, LocalDate birthday, String email) {
		validateLoginId(loginId);
		validateName(name);
		validateEmail(email);
		validateBirthday(birthday);

		Password password = Password.create(rawPassword, birthday);
		return new User(null, loginId, password, name, birthday, email);
	}

	public static User reconstruct(Long id, String loginId, String encodedPassword, String name, LocalDate birthday, String email) {
		Password password = Password.fromEncoded(encodedPassword);
		return new User(id, loginId, password, name, birthday, email);
	}

	public Long getId() {
		return id;
	}

	public String getLoginId() {
		return loginId;
	}

	public Password getPassword() {
		return password;
	}

	public String getName() {
		return name;
	}

	public LocalDate getBirthday() {
		return birthday;
	}

	public String getEmail() {
		return email;
	}

	public void authenticate(String rawPassword) {
		if (!this.password.matches(rawPassword)) {
			throw new CoreException(ErrorType.UNAUTHORIZED);
		}
	}

	private static void validateLoginId(String loginId) {
		if (loginId == null ||
			loginId.isEmpty() ||
			loginId.length() > LOGIN_ID_MAX_LENGTH ||
			!LOGIN_ID_PATTERN.matcher(loginId).matches()) {
			throw new CoreException(ErrorType.INVALID_LOGIN_ID_FORMAT);
		}
	}

	private static void validateName(String name) {
		if (name == null ||
			name.isEmpty() ||
			name.length() > NAME_MAX_LENGTH ||
			!NAME_PATTERN.matcher(name).matches()) {
			throw new CoreException(ErrorType.INVALID_NAME_FORMAT);
		}
	}

	private static void validateEmail(String email) {
		if (email == null ||
			email.isEmpty() ||
			email.length() > EMAIL_MAX_LENGTH ||
			!EMAIL_PATTERN.matcher(email).matches()) {
			throw new CoreException(ErrorType.INVALID_EMAIL_FORMAT);
		}

		String domain = email.substring(email.indexOf('@') + 1);
		if (EMAIL_DOMAIN_INVALID_PATTERN.matcher(domain).find()) {
			throw new CoreException(ErrorType.INVALID_EMAIL_FORMAT);
		}
	}

	private static void validateBirthday(LocalDate birthday) {
		if (birthday == null ||
			birthday.isAfter(LocalDate.now()) ||
			birthday.isBefore(MIN_BIRTHDAY)) {
			throw new CoreException(ErrorType.INVALID_BIRTHDAY);
		}
	}
}
