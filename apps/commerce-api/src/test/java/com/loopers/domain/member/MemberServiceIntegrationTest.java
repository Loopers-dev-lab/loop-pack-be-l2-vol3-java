package com.loopers.domain.member;

import com.loopers.support.crypto.PasswordEncoder;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class MemberServiceIntegrationTest {

	@Autowired
	private MemberService memberService;

	@Autowired
	private DatabaseCleanUp databaseCleanUp;

	@AfterEach
	void tearDown() {
		databaseCleanUp.truncateAllTables();
	}

	@DisplayName("회원가입 후 조회할 때,")
	@Nested
	class RegisterAndGetMyInfo {

		@DisplayName("회원가입 후 해당 계정으로 조회하면, 회원 정보가 정상 반환된다.")
		@Test
		void registerAndGetMyInfoSuccess() {
			// given
			String loginId = "testuser";
			String password = "password1!@";
			String name = "홍길동";
			LocalDate birthDate = LocalDate.of(2000, 6, 5);
			String email = "test@example.com";

			memberService.register(loginId, password, name, birthDate, email);

			// when
			MemberModel result = memberService.getMyInfo(loginId, password);

			// then
			assertAll(
					() -> assertThat(result.getLoginId()).isEqualTo(loginId),
					() -> assertThat(result.getName()).isEqualTo(name),
					() -> assertThat(result.getBirthDate()).isEqualTo(birthDate),
					() -> assertThat(result.getEmail()).isEqualTo(email),
					() -> assertThat(result.getPassword()).isEqualTo(PasswordEncoder.encode(password))
			);
		}
	}

	@DisplayName("중복 loginId로 가입할 때,")
	@Nested
	class DuplicateLoginId {

		@DisplayName("이미 존재하는 loginId로 가입하면, CONFLICT 예외가 발생한다.")
		@Test
		void failWithDuplicateLoginId() {
			// given
			String loginId = "testuser";
			memberService.register(loginId, "password1!@", "홍길동", LocalDate.of(2000, 6, 5), "test@example.com");

			// when
			CoreException result = assertThrows(CoreException.class, () ->
					memberService.register(loginId, "other1234!@", "김철수", LocalDate.of(1995, 3, 10), "other@example.com")
			);

			// then
			assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
		}
	}

	@DisplayName("존재하지 않는 회원을 조회할 때,")
	@Nested
	class NotFoundMember {

		@DisplayName("존재하지 않는 loginId로 조회하면, NOT_FOUND 예외가 발생한다.")
		@Test
		void failWithNotFoundMember() {
			// given
			String loginId = "nonexistent";
			String password = "password1!@";

			// when
			CoreException result = assertThrows(CoreException.class, () ->
					memberService.getMyInfo(loginId, password)
			);

			// then
			assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
		}
	}

	@DisplayName("비밀번호를 변경할 때,")
	@Nested
	class ChangePassword {

		@DisplayName("비밀번호 변경 후 새 비밀번호로 조회하면, 정상 반환된다.")
		@Test
		void changePasswordAndGetMyInfoSuccess() {
			// given
			String loginId = "testuser";
			String currentPassword = "password1!@";
			String newPassword = "newpass1!@#";
			memberService.register(loginId, currentPassword, "홍길동", LocalDate.of(2000, 6, 5), "test@example.com");

			// when
			memberService.changePassword(loginId, currentPassword, newPassword);

			// then
			MemberModel result = memberService.getMyInfo(loginId, newPassword);
			assertAll(
					() -> assertThat(result.getLoginId()).isEqualTo(loginId),
					() -> assertThat(result.getPassword()).isEqualTo(PasswordEncoder.encode(newPassword))
			);
		}
	}
}
