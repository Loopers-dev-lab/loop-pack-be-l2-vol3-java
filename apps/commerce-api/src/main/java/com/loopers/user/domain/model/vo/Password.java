package com.loopers.user.domain.model.vo;

import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.regex.Pattern;

/**
 * 비밀번호 값 객체
 * - value: 인코딩된 비밀번호 값
 */
public record Password(String value) {

	private static final int MIN_LENGTH = 8;
	private static final int MAX_LENGTH = 16;
	private static final Pattern UPPERCASE_PATTERN = Pattern.compile("[A-Z]");
	private static final Pattern LOWERCASE_PATTERN = Pattern.compile("[a-z]");
	private static final Pattern DIGIT_PATTERN = Pattern.compile("[0-9]");
	private static final Pattern SPECIAL_CHAR_PATTERN = Pattern.compile("[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]");
	private static final Pattern ALLOWED_CHARS_PATTERN = Pattern.compile("^[a-zA-Z0-9!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]+$");


	/**
	 * 도메인 로직
	 * 1. 원문 비밀번호 생성
	 * 2. 인코딩 비밀번호 복원
	 * 3. 비밀번호 일치 여부 검증
	 */

	// 1. 원문 비밀번호 생성
	public static Password create(String rawPassword, LocalDate birthday) {

		// 비밀번호 형식 검증
		validateFormat(rawPassword);

		// 생년월일 포함 여부 검증
		validateNotContainsBirthday(rawPassword, birthday);

		// 비밀번호 인코딩 후 값 객체 생성
		return new Password(encode(rawPassword));
	}

	// 2. 인코딩 비밀번호 복원
	public static Password fromEncoded(String encodedPassword) {
		return new Password(encodedPassword);
	}

	// 3. 비밀번호 일치 여부 검증
	public boolean matches(String rawPassword) {
		if (rawPassword == null) {
			return false;
		}
		return this.value.equals(encode(rawPassword));
	}

	private static void validateFormat(String rawPassword) {

		// 길이/허용문자/문자 조합 규칙 검증
		if (rawPassword == null ||
			rawPassword.length() < MIN_LENGTH ||
			rawPassword.length() > MAX_LENGTH ||
			!ALLOWED_CHARS_PATTERN.matcher(rawPassword).matches() ||
			!UPPERCASE_PATTERN.matcher(rawPassword).find() ||
			!LOWERCASE_PATTERN.matcher(rawPassword).find() ||
			!DIGIT_PATTERN.matcher(rawPassword).find() ||
			!SPECIAL_CHAR_PATTERN.matcher(rawPassword).find()) {
			throw new CoreException(ErrorType.INVALID_PASSWORD_FORMAT);
		}
	}

	private static void validateNotContainsBirthday(String rawPassword, LocalDate birthday) {

		// 생년월일 패턴 후보 생성
		String yyyymmdd = birthday.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
		String yymmdd = birthday.format(DateTimeFormatter.ofPattern("yyMMdd"));
		String yyyyDashMmDashDd = birthday.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

		// 비밀번호에 생년월일이 포함되면 예외 반환
		if (rawPassword.contains(yyyymmdd) || rawPassword.contains(yymmdd) || rawPassword.contains(yyyyDashMmDashDd)) {
			throw new CoreException(ErrorType.PASSWORD_CONTAINS_BIRTHDAY);
		}
	}

	private static String encode(String rawPassword) {
		try {

			// SHA-256 해시 후 Base64 문자열로 인코딩
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(rawPassword.getBytes(StandardCharsets.UTF_8));
			return Base64.getEncoder().encodeToString(hash);
		} catch (NoSuchAlgorithmException e) {
			throw new CoreException(ErrorType.INTERNAL_ERROR);
		}
	}
}
