package com.loopers.user.interfaces.controller.response;

import com.loopers.user.domain.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@DisplayName("UserMeResponse 테스트")
class UserMeResponseTest {

	private static final String VALID_LOGIN_ID = "testuser01";
	private static final LocalDate VALID_BIRTHDAY = LocalDate.of(1990, 1, 15);
	private static final String VALID_EMAIL = "test@example.com";

	@Test
	@DisplayName("[from()] User 도메인 객체 -> UserMeResponse 변환. loginId, maskedName, birthday, email 매핑")
	void fromUserThenMappedCorrectly() {
		// Arrange
		User user = User.reconstruct(1L, VALID_LOGIN_ID, "encodedPw", "홍길동", VALID_BIRTHDAY, VALID_EMAIL);

		// Act
		UserMeResponse response = UserMeResponse.from(user);

		// Assert
		assertAll(
			() -> assertThat(response.loginId()).isEqualTo(VALID_LOGIN_ID),
			() -> assertThat(response.name()).isEqualTo("홍길*"),
			() -> assertThat(response.birthday()).isEqualTo(VALID_BIRTHDAY),
			() -> assertThat(response.email()).isEqualTo(VALID_EMAIL)
		);
	}

	@Test
	@DisplayName("[from()] 이름 3자(홍길동) -> 마지막 글자 마스킹(홍길*)")
	void fromUserNameMasking3Chars() {
		// Arrange
		User user = User.reconstruct(1L, VALID_LOGIN_ID, "encodedPw", "홍길동", VALID_BIRTHDAY, VALID_EMAIL);

		// Act
		UserMeResponse response = UserMeResponse.from(user);

		// Assert
		assertThat(response.name()).isEqualTo("홍길*");
	}

	@Test
	@DisplayName("[from()] 이름 2자(홍길) -> 마지막 글자 마스킹(홍*)")
	void fromUserNameMasking2Chars() {
		// Arrange
		User user = User.reconstruct(1L, VALID_LOGIN_ID, "encodedPw", "홍길", VALID_BIRTHDAY, VALID_EMAIL);

		// Act
		UserMeResponse response = UserMeResponse.from(user);

		// Assert
		assertThat(response.name()).isEqualTo("홍*");
	}

	@Test
	@DisplayName("[from()] 이름 1자(김) -> 전체 마스킹(*)")
	void fromUserNameMasking1Char() {
		// Arrange
		User user = User.reconstruct(1L, VALID_LOGIN_ID, "encodedPw", "김", VALID_BIRTHDAY, VALID_EMAIL);

		// Act
		UserMeResponse response = UserMeResponse.from(user);

		// Assert
		assertThat(response.name()).isEqualTo("*");
	}

	@Test
	@DisplayName("[from()] 영문 이름(John) -> 마지막 글자 마스킹(Joh*)")
	void fromUserNameMaskingEnglish() {
		// Arrange
		User user = User.reconstruct(1L, VALID_LOGIN_ID, "encodedPw", "John", VALID_BIRTHDAY, VALID_EMAIL);

		// Act
		UserMeResponse response = UserMeResponse.from(user);

		// Assert
		assertThat(response.name()).isEqualTo("Joh*");
	}
}
