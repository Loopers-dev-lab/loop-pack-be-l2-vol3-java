package com.loopers.user.interfaces.controller.response;

import com.loopers.user.application.dto.out.UserMeOutDto;
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
	@DisplayName("[UserMeResponse.from()] UserMeOutDto -> UserMeResponse 변환. loginId, maskedName, birthday, email 매핑")
	void fromOutDtoThenMappedCorrectly() {
		// Arrange
		UserMeOutDto outDto = new UserMeOutDto(VALID_LOGIN_ID, "홍길동", VALID_BIRTHDAY, VALID_EMAIL);

		// Act
		UserMeResponse response = UserMeResponse.from(outDto);

		// Assert
		assertAll(
			() -> assertThat(response.loginId()).isEqualTo(VALID_LOGIN_ID),
			() -> assertThat(response.name()).isEqualTo("홍길*"),
			() -> assertThat(response.birthday()).isEqualTo(VALID_BIRTHDAY),
			() -> assertThat(response.email()).isEqualTo(VALID_EMAIL)
		);
	}

	@Test
	@DisplayName("[UserMeResponse.from()] 이름 3자(홍길동) -> 마지막 글자 마스킹(홍길*)")
	void fromOutDtoNameMasking3Chars() {
		// Arrange
		UserMeOutDto outDto = new UserMeOutDto(VALID_LOGIN_ID, "홍길동", VALID_BIRTHDAY, VALID_EMAIL);

		// Act
		UserMeResponse response = UserMeResponse.from(outDto);

		// Assert
		assertThat(response.name()).isEqualTo("홍길*");
	}

	@Test
	@DisplayName("[UserMeResponse.from()] 이름 2자(홍길) -> 마지막 글자 마스킹(홍*)")
	void fromOutDtoNameMasking2Chars() {
		// Arrange
		UserMeOutDto outDto = new UserMeOutDto(VALID_LOGIN_ID, "홍길", VALID_BIRTHDAY, VALID_EMAIL);

		// Act
		UserMeResponse response = UserMeResponse.from(outDto);

		// Assert
		assertThat(response.name()).isEqualTo("홍*");
	}

	@Test
	@DisplayName("[UserMeResponse.from()] 이름 1자(김) -> 전체 마스킹(*)")
	void fromOutDtoNameMasking1Char() {
		// Arrange
		UserMeOutDto outDto = new UserMeOutDto(VALID_LOGIN_ID, "김", VALID_BIRTHDAY, VALID_EMAIL);

		// Act
		UserMeResponse response = UserMeResponse.from(outDto);

		// Assert
		assertThat(response.name()).isEqualTo("*");
	}

	@Test
	@DisplayName("[UserMeResponse.from()] 이름 null -> 전체 마스킹(*)")
	void fromOutDtoNameMaskingNull() {
		// Arrange
		UserMeOutDto outDto = new UserMeOutDto(VALID_LOGIN_ID, null, VALID_BIRTHDAY, VALID_EMAIL);

		// Act
		UserMeResponse response = UserMeResponse.from(outDto);

		// Assert
		assertThat(response.name()).isEqualTo("*");
	}

	@Test
	@DisplayName("[UserMeResponse.from()] 이름 빈 문자열(\"\") -> 전체 마스킹(*)")
	void fromOutDtoNameMaskingEmptyString() {
		// Arrange
		UserMeOutDto outDto = new UserMeOutDto(VALID_LOGIN_ID, "", VALID_BIRTHDAY, VALID_EMAIL);

		// Act
		UserMeResponse response = UserMeResponse.from(outDto);

		// Assert
		assertThat(response.name()).isEqualTo("*");
	}

	@Test
	@DisplayName("[UserMeResponse.from()] 영문 이름(John) -> 마지막 글자 마스킹(Joh*)")
	void fromOutDtoNameMaskingEnglish() {
		// Arrange
		UserMeOutDto outDto = new UserMeOutDto(VALID_LOGIN_ID, "John", VALID_BIRTHDAY, VALID_EMAIL);

		// Act
		UserMeResponse response = UserMeResponse.from(outDto);

		// Assert
		assertThat(response.name()).isEqualTo("Joh*");
	}
}
