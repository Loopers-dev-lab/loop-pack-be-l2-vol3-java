package com.loopers.domain.member;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MemberModelTest {

	@DisplayName("회원을 생성할 때,")
	@Nested
	class Create {

		@DisplayName("모든 정보가 유효하면, 정상적으로 생성된다.")
		@Test
		void createMember() {
			// given
			String loginId = "testuser";
			String password = "password1!@";
			String name = "홍길동";
			LocalDate birthDate = LocalDate.of(2000, 6, 5);
			String email = "test@example.com";

			// when
			MemberModel member = new MemberModel(loginId, password, name, birthDate, email);

			// then
			assertAll(
					() -> assertThat(member.getLoginId()).isEqualTo(loginId),
					() -> assertThat(member.getPassword()).isEqualTo(password),
					() -> assertThat(member.getName()).isEqualTo(name),
					() -> assertThat(member.getBirthDate()).isEqualTo(birthDate),
					() -> assertThat(member.getEmail()).isEqualTo(email)
			);
		}

		@DisplayName("loginId가 null이면, BAD_REQUEST 예외가 발생한다.")
		@Test
		void failWithNullLoginId() {
			// given & when
			CoreException result = assertThrows(CoreException.class, () ->
					new MemberModel(null, "password1!@", "홍길동", LocalDate.of(2000, 6, 5), "test@example.com")
			);

			// then
			assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
		}

		@DisplayName("loginId가 빈값이면, BAD_REQUEST 예외가 발생한다.")
		@Test
		void failWithEmptyLoginId() {
			// given & when
			CoreException result = assertThrows(CoreException.class, () ->
					new MemberModel(" ", "password1!@", "홍길동", LocalDate.of(2000, 6, 5), "test@example.com")
			);

			// then
			assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
		}

		@DisplayName("loginId에 특수문자가 포함되면, BAD_REQUEST 예외가 발생한다.")
		@Test
		void failWithSpecialCharLoginId() {
			// given & when
			CoreException result = assertThrows(CoreException.class, () ->
					new MemberModel("test@user!", "password1!@", "홍길동", LocalDate.of(2000, 6, 5), "test@example.com")
			);

			// then
			assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
		}

		@DisplayName("loginId에 한글이 포함되면, BAD_REQUEST 예외가 발생한다.")
		@Test
		void failWithKoreanLoginId() {
			// given & when
			CoreException result = assertThrows(CoreException.class, () ->
					new MemberModel("테스트유저", "password1!@", "홍길동", LocalDate.of(2000, 6, 5), "test@example.com")
			);

			// then
			assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
		}

		@DisplayName("password에 허용되지 않는 문자(한글)가 포함되면, BAD_REQUEST 예외가 발생한다.")
		@Test
		void failWithKoreanPassword() {
			// given & when
			CoreException result = assertThrows(CoreException.class, () ->
					new MemberModel("testuser", "비밀번호한글입력!", "홍길동", LocalDate.of(2000, 6, 5), "test@example.com")
			);

			// then
			assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
		}

		@DisplayName("password에 공백이 포함되면, BAD_REQUEST 예외가 발생한다.")
		@Test
		void failWithSpaceInPassword() {
			// given & when
			CoreException result = assertThrows(CoreException.class, () ->
					new MemberModel("testuser", "pass word1!", "홍길동", LocalDate.of(2000, 6, 5), "test@example.com")
			);

			// then
			assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
		}

		@DisplayName("password가 8자 미만이면, BAD_REQUEST 예외가 발생한다.")
		@Test
		void failWithShortPassword() {
			// given & when
			CoreException result = assertThrows(CoreException.class, () ->
					new MemberModel("testuser", "pass1!", "홍길동", LocalDate.of(2000, 6, 5), "test@example.com")
			);

			// then
			assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
		}

		@DisplayName("password가 16자 초과이면, BAD_REQUEST 예외가 발생한다.")
		@Test
		void failWithLongPassword() {
			// given & when
			CoreException result = assertThrows(CoreException.class, () ->
					new MemberModel("testuser", "a".repeat(17), "홍길동", LocalDate.of(2000, 6, 5), "test@example.com")
			);

			// then
			assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
		}

		@DisplayName("password에 생년월일이 포함되면, BAD_REQUEST 예외가 발생한다.")
		@Test
		void failWithBirthDateInPassword() {
			// given & when
			CoreException result = assertThrows(CoreException.class, () ->
					new MemberModel("testuser", "pass20000605!", "홍길동", LocalDate.of(2000, 6, 5), "test@example.com")
			);

			// then
			assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
		}

		@DisplayName("name이 null이면, BAD_REQUEST 예외가 발생한다.")
		@Test
		void failWithNullName() {
			// given & when
			CoreException result = assertThrows(CoreException.class, () ->
					new MemberModel("testuser", "password1!@", null, LocalDate.of(2000, 6, 5), "test@example.com")
			);

			// then
			assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
		}

		@DisplayName("name이 빈값이면, BAD_REQUEST 예외가 발생한다.")
		@Test
		void failWithEmptyName() {
			// given & when
			CoreException result = assertThrows(CoreException.class, () ->
					new MemberModel("testuser", "password1!@", " ", LocalDate.of(2000, 6, 5), "test@example.com")
			);

			// then
			assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
		}

		@DisplayName("email 형식이 아니면, BAD_REQUEST 예외가 발생한다.")
		@Test
		void failWithInvalidEmail() {
			// given & when
			CoreException result = assertThrows(CoreException.class, () ->
					new MemberModel("testuser", "password1!@", "홍길동", LocalDate.of(2000, 6, 5), "invalid-email")
			);

			// then
			assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
		}

		@DisplayName("birthDate가 null이면, BAD_REQUEST 예외가 발생한다.")
		@Test
		void failWithNullBirthDate() {
			// given & when
			CoreException result = assertThrows(CoreException.class, () ->
					new MemberModel("testuser", "password1!@", "홍길동", null, "test@example.com")
			);

			// then
			assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
		}
	}

	@DisplayName("이름을 마스킹할 때,")
	@Nested
	class MaskedName {

		@DisplayName("마지막 글자가 *로 마스킹된다.")
		@Test
		void maskedLastCharacter() {
			// given
			MemberModel member = new MemberModel("testuser", "password1!@", "홍길동", LocalDate.of(2000, 6, 5), "test@example.com");

			// when
			String result = member.getMaskedName();

			// then
			assertThat(result).isEqualTo("홍길*");
		}

		@DisplayName("한 글자 이름이면, *로 마스킹된다.")
		@Test
		void maskedSingleCharacterName() {
			// given
			MemberModel member = new MemberModel("testuser", "password1!@", "홍", LocalDate.of(2000, 6, 5), "test@example.com");

			// when
			String result = member.getMaskedName();

			// then
			assertThat(result).isEqualTo("*");
		}
	}

	@DisplayName("비밀번호를 변경할 때,")
	@Nested
	class ChangePassword {

		@DisplayName("유효한 새 비밀번호로 변경하면, 비밀번호가 변경된다.")
		@Test
		void changePasswordSuccess() {
			// given
			MemberModel member = new MemberModel("testuser", "password1!@", "홍길동", LocalDate.of(2000, 6, 5), "test@example.com");
			String newPassword = "newpass1!@";

			// when
			member.changePassword(newPassword);

			// then
			assertThat(member.getPassword()).isEqualTo(newPassword);
		}

		@DisplayName("새 비밀번호에 허용되지 않는 문자(한글)가 포함되면, BAD_REQUEST 예외가 발생한다.")
		@Test
		void failWithKoreanNewPassword() {
			// given
			MemberModel member = new MemberModel("testuser", "password1!@", "홍길동", LocalDate.of(2000, 6, 5), "test@example.com");

			// when
			CoreException result = assertThrows(CoreException.class, () ->
					member.changePassword("새비밀번호입력!@")
			);

			// then
			assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
		}

		@DisplayName("새 비밀번호가 8자 미만이면, BAD_REQUEST 예외가 발생한다.")
		@Test
		void failWithShortNewPassword() {
			// given
			MemberModel member = new MemberModel("testuser", "password1!@", "홍길동", LocalDate.of(2000, 6, 5), "test@example.com");

			// when
			CoreException result = assertThrows(CoreException.class, () ->
					member.changePassword("short!")
			);

			// then
			assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
		}

		@DisplayName("새 비밀번호가 16자 초과이면, BAD_REQUEST 예외가 발생한다.")
		@Test
		void failWithLongNewPassword() {
			// given
			MemberModel member = new MemberModel("testuser", "password1!@", "홍길동", LocalDate.of(2000, 6, 5), "test@example.com");

			// when
			CoreException result = assertThrows(CoreException.class, () ->
					member.changePassword("a".repeat(17))
			);

			// then
			assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
		}

		@DisplayName("새 비밀번호에 생년월일이 포함되면, BAD_REQUEST 예외가 발생한다.")
		@Test
		void failWithBirthDateInNewPassword() {
			// given
			MemberModel member = new MemberModel("testuser", "password1!@", "홍길동", LocalDate.of(2000, 6, 5), "test@example.com");

			// when
			CoreException result = assertThrows(CoreException.class, () ->
					member.changePassword("pass20000605!")
			);

			// then
			assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
		}
	}
}
