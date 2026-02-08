package com.loopers.user.domain.model;


import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import com.loopers.user.domain.model.vo.Password;

import java.time.LocalDate;
import java.util.regex.Pattern;


/**
 * 유저
 * - id: 유저 ID
 * - loginId: 로그인 ID
 * - password: 비밀번호
 * - name: 이름
 * - birthday: 생년월일
 * - email: 이메일
 */
public class User {

	private static final int LOGIN_ID_MIN_LENGTH = 4;
	private static final int LOGIN_ID_MAX_LENGTH = 20;
	private static final int NAME_MAX_LENGTH = 50;
	private static final int EMAIL_MAX_LENGTH = 254;
	private static final LocalDate MIN_BIRTHDAY = LocalDate.of(1900, 1, 1);

	private static final Pattern LOGIN_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9]+$");
	private static final Pattern NAME_PATTERN = Pattern.compile("^[가-힣a-zA-Z ]+$");
	private static final Pattern EMAIL_PATTERN = Pattern.compile("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");
	private static final Pattern EMAIL_DOMAIN_INVALID_PATTERN = Pattern.compile("(\\.\\.|^\\.|\\.$)");

	private Long id;
	private final String loginId;
	private Password password;
	private final String name;
	private final LocalDate birthday;
	private final String email;


	/**
	 * 도메인 로직
	 * 1. 회원가입용 유저 생성
	 * 2. 저장된 유저 복원
	 * 3. 비밀번호 인증
	 * 4. 비밀번호 변경
	 */

	private User(Long id, String loginId, Password password, String name, LocalDate birthday, String email) {
		this.id = id;
		this.loginId = loginId;
		this.password = password;
		this.name = name;
		this.birthday = birthday;
		this.email = email;
	}


	// 1. 회원가입용 유저 생성
	public static User create(String loginId, String rawPassword, String name, LocalDate birthday, String email) {

		// 입력값 정규화
		String normalizedLoginId = loginId != null ? loginId.trim().toLowerCase() : null;
		String trimmedName = name != null ? name.trim() : null;
		String trimmedEmail = email != null ? email.trim() : null;

		// 회원가입 필드 유효성 검증
		validateLoginId(normalizedLoginId);
		validateName(trimmedName);
		validateEmail(trimmedEmail);
		validateBirthday(birthday);

		// 비밀번호 생성 후 유저 도메인 객체 생성
		Password password = Password.create(rawPassword, birthday);
		return new User(null, normalizedLoginId, password, trimmedName, birthday, trimmedEmail);
	}


	// 2. 저장된 유저 복원
	public static User reconstruct(Long id, String loginId, String encodedPassword, String name, LocalDate birthday,
		String email) {

		// 저장된 인코딩 비밀번호를 값 객체로 복원
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


	// 3. 비밀번호 인증
	public void authenticate(String rawPassword) {

		// 입력 비밀번호와 저장된 비밀번호 일치 여부 검증
		if (!this.password.matches(rawPassword)) {
			throw new CoreException(ErrorType.UNAUTHORIZED);
		}
	}


	// 4. 비밀번호 변경
	public void changePassword(String currentRawPassword, String newRawPassword) {

		// 현재/신규 비밀번호 필수값 검증
		if (currentRawPassword == null || newRawPassword == null) {
			throw new CoreException(ErrorType.UNAUTHORIZED);
		}

		// 현재 비밀번호 인증
		authenticate(currentRawPassword);

		// 현재 비밀번호와 신규 비밀번호 동일 여부 검증
		if (currentRawPassword.equals(newRawPassword)) {
			throw new CoreException(ErrorType.PASSWORD_SAME_AS_CURRENT);
		}

		// 신규 비밀번호로 교체
		this.password = Password.create(newRawPassword, this.birthday);
	}


	private static void validateLoginId(String loginId) {

		// 로그인 ID 형식 검증
		if (loginId == null ||
			loginId.isBlank() ||
			loginId.length() < LOGIN_ID_MIN_LENGTH ||
			loginId.length() > LOGIN_ID_MAX_LENGTH ||
			!LOGIN_ID_PATTERN.matcher(loginId).matches()) {
			throw new CoreException(ErrorType.INVALID_LOGIN_ID_FORMAT);
		}
	}


	private static void validateName(String name) {

		// 이름 형식 검증
		if (name == null ||
			name.isBlank() ||
			name.length() > NAME_MAX_LENGTH ||
			!NAME_PATTERN.matcher(name).matches()) {
			throw new CoreException(ErrorType.INVALID_NAME_FORMAT);
		}
	}


	private static void validateEmail(String email) {

		// 이메일 기본 형식 검증
		if (email == null ||
			email.isBlank() ||
			email.length() > EMAIL_MAX_LENGTH ||
			!EMAIL_PATTERN.matcher(email).matches()) {
			throw new CoreException(ErrorType.INVALID_EMAIL_FORMAT);
		}

		// 도메인 구간의 비정상 패턴 검증
		String domain = email.substring(email.indexOf('@') + 1);
		if (EMAIL_DOMAIN_INVALID_PATTERN.matcher(domain).find()) {
			throw new CoreException(ErrorType.INVALID_EMAIL_FORMAT);
		}
	}


	private static void validateBirthday(LocalDate birthday) {

		// 생년월일 범위 검증
		if (birthday == null ||
			!birthday.isAfter(MIN_BIRTHDAY) ||
			!birthday.isBefore(LocalDate.now())) {
			throw new CoreException(ErrorType.INVALID_BIRTHDAY);
		}
	}

}
