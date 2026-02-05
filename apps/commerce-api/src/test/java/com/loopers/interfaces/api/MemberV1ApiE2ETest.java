package com.loopers.interfaces.api;

import com.loopers.domain.member.MemberService;
import com.loopers.interfaces.api.member.MemberV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MemberV1ApiE2ETest {

	private static final String ENDPOINT_REGISTER = "/api/v1/members";
	private static final String ENDPOINT_ME = "/api/v1/members/me";
	private static final String ENDPOINT_CHANGE_PASSWORD = "/api/v1/members/me/password";

	private final TestRestTemplate testRestTemplate;
	private final MemberService memberService;
	private final DatabaseCleanUp databaseCleanUp;

	@Autowired
	public MemberV1ApiE2ETest(
			TestRestTemplate testRestTemplate,
			MemberService memberService,
			DatabaseCleanUp databaseCleanUp
	) {
		this.testRestTemplate = testRestTemplate;
		this.memberService = memberService;
		this.databaseCleanUp = databaseCleanUp;
	}

	@AfterEach
	void tearDown() {
		databaseCleanUp.truncateAllTables();
	}

	private HttpHeaders authHeaders(String loginId, String loginPw) {
		HttpHeaders headers = new HttpHeaders();
		headers.set("X-Loopers-LoginId", loginId);
		headers.set("X-Loopers-LoginPw", loginPw);
		return headers;
	}

	@DisplayName("POST /api/v1/members")
	@Nested
	class Register {

		@DisplayName("유효한 정보로 회원가입하면, 200 응답을 받는다.")
		@Test
		void registerSuccess() {
			// given
			MemberV1Dto.RegisterRequest request = new MemberV1Dto.RegisterRequest(
					"testuser", "password1!@", "홍길동", LocalDate.of(2000, 6, 5), "test@example.com"
			);

			// when
			ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
			ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
					ENDPOINT_REGISTER, HttpMethod.POST, new HttpEntity<>(request), responseType
			);

			// then
			assertAll(
					() -> assertThat(response.getStatusCode().is2xxSuccessful()).isTrue(),
					() -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS)
			);
		}

		@DisplayName("중복된 loginId로 회원가입하면, 409 CONFLICT 응답을 받는다.")
		@Test
		void failWithDuplicateLoginId() {
			// given
			memberService.register("testuser", "password1!@", "홍길동", LocalDate.of(2000, 6, 5), "test@example.com");

			MemberV1Dto.RegisterRequest request = new MemberV1Dto.RegisterRequest(
					"testuser", "other1234!@", "김철수", LocalDate.of(1995, 3, 10), "other@example.com"
			);

			// when
			ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
			ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
					ENDPOINT_REGISTER, HttpMethod.POST, new HttpEntity<>(request), responseType
			);

			// then
			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		}
	}

	@DisplayName("GET /api/v1/members/me")
	@Nested
	class GetMyInfo {

		@DisplayName("유효한 인증 정보로 조회하면, 마스킹된 이름이 포함된 200 응답을 받는다.")
		@Test
		void getMyInfoSuccess() {
			// given
			String loginId = "testuser";
			String password = "password1!@";
			memberService.register(loginId, password, "홍길동", LocalDate.of(2000, 6, 5), "test@example.com");

			HttpHeaders headers = authHeaders(loginId, password);

			// when
			ParameterizedTypeReference<ApiResponse<MemberV1Dto.MyInfoResponse>> responseType = new ParameterizedTypeReference<>() {};
			ResponseEntity<ApiResponse<MemberV1Dto.MyInfoResponse>> response = testRestTemplate.exchange(
					ENDPOINT_ME, HttpMethod.GET, new HttpEntity<>(headers), responseType
			);

			// then
			assertAll(
					() -> assertThat(response.getStatusCode().is2xxSuccessful()).isTrue(),
					() -> assertThat(response.getBody().data().loginId()).isEqualTo(loginId),
					() -> assertThat(response.getBody().data().maskedName()).isEqualTo("홍길*"),
					() -> assertThat(response.getBody().data().birthDate()).isEqualTo(LocalDate.of(2000, 6, 5)),
					() -> assertThat(response.getBody().data().email()).isEqualTo("test@example.com")
			);
		}

		@DisplayName("잘못된 비밀번호로 조회하면, 400 BAD_REQUEST 응답을 받는다.")
		@Test
		void failWithWrongPassword() {
			// given
			String loginId = "testuser";
			memberService.register(loginId, "password1!@", "홍길동", LocalDate.of(2000, 6, 5), "test@example.com");

			HttpHeaders headers = authHeaders(loginId, "wrongpass1!@");

			// when
			ParameterizedTypeReference<ApiResponse<MemberV1Dto.MyInfoResponse>> responseType = new ParameterizedTypeReference<>() {};
			ResponseEntity<ApiResponse<MemberV1Dto.MyInfoResponse>> response = testRestTemplate.exchange(
					ENDPOINT_ME, HttpMethod.GET, new HttpEntity<>(headers), responseType
			);

			// then
			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		}

		@DisplayName("인증 헤더 없이 조회하면, 400 BAD_REQUEST 응답을 받는다.")
		@Test
		void failWithoutAuthHeaders() {
			// given & when
			ParameterizedTypeReference<ApiResponse<MemberV1Dto.MyInfoResponse>> responseType = new ParameterizedTypeReference<>() {};
			ResponseEntity<ApiResponse<MemberV1Dto.MyInfoResponse>> response = testRestTemplate.exchange(
					ENDPOINT_ME, HttpMethod.GET, new HttpEntity<>(null), responseType
			);

			// then
			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		}
	}

	@DisplayName("PATCH /api/v1/members/me/password")
	@Nested
	class ChangePassword {

		@DisplayName("유효한 인증 정보와 새 비밀번호로 변경하면, 200 응답을 받는다.")
		@Test
		void changePasswordSuccess() {
			// given
			String loginId = "testuser";
			String currentPassword = "password1!@";
			String newPassword = "newpass1!@#";
			memberService.register(loginId, currentPassword, "홍길동", LocalDate.of(2000, 6, 5), "test@example.com");

			HttpHeaders headers = authHeaders(loginId, currentPassword);
			MemberV1Dto.ChangePasswordRequest request = new MemberV1Dto.ChangePasswordRequest(newPassword);

			// when
			ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
			ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
					ENDPOINT_CHANGE_PASSWORD, HttpMethod.PATCH, new HttpEntity<>(request, headers), responseType
			);

			// then
			assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
		}

		@DisplayName("현재 비밀번호와 동일한 비밀번호로 변경하면, 400 BAD_REQUEST 응답을 받는다.")
		@Test
		void failWithSamePassword() {
			// given
			String loginId = "testuser";
			String password = "password1!@";
			memberService.register(loginId, password, "홍길동", LocalDate.of(2000, 6, 5), "test@example.com");

			HttpHeaders headers = authHeaders(loginId, password);
			MemberV1Dto.ChangePasswordRequest request = new MemberV1Dto.ChangePasswordRequest(password);

			// when
			ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
			ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
					ENDPOINT_CHANGE_PASSWORD, HttpMethod.PATCH, new HttpEntity<>(request, headers), responseType
			);

			// then
			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		}
	}
}
