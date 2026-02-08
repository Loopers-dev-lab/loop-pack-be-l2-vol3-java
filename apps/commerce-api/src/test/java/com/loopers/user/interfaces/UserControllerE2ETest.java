package com.loopers.user.interfaces;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.user.interfaces.controller.request.UserSignUpRequest;
import com.loopers.support.common.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasKey;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})
@DisplayName("UserController E2E 테스트")
class UserControllerE2ETest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private DatabaseCleanUp databaseCleanUp;

	@AfterEach
	void tearDown() {
		databaseCleanUp.truncateAllTables();
	}

	@Nested
	@DisplayName("POST /api/v1/users - 회원가입")
	class SignUpTest {

		@Test
		@DisplayName("[POST /api/v1/users] 유효한 회원가입 요청 -> 201 Created. "
			+ "응답: id, loginId, name, birthday, email 포함")
		void signUpSuccess() throws Exception {
			// Arrange
			UserSignUpRequest request = new UserSignUpRequest(
				"testuser01",
				"Test1234!",
				"홍길동",
				LocalDate.of(1990, 1, 15),
				"test@example.com"
			);

			// Act & Assert
			mockMvc.perform(post("/api/v1/users")
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.loginId").value("testuser01"))
				.andExpect(jsonPath("$.name").value("홍길동"))
				.andExpect(jsonPath("$.email").value("test@example.com"))
				.andExpect(jsonPath("$.birthday").value("1990-01-15"));
		}

		@Test
		@DisplayName("[POST /api/v1/users] 중복 로그인 ID -> 409 Conflict. "
			+ "에러 코드: USER_ALREADY_EXISTS")
		void signUpFailDuplicateLoginId() throws Exception {
			// Arrange
			UserSignUpRequest request = new UserSignUpRequest(
				"testuser01",
				"Test1234!",
				"홍길동",
				LocalDate.of(1990, 1, 15),
				"test@example.com"
			);

			// 첫 번째 회원가입
			mockMvc.perform(post("/api/v1/users")
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isCreated());

			// Act & Assert - 동일 loginId로 재시도
			mockMvc.perform(post("/api/v1/users")
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value(ErrorType.USER_ALREADY_EXISTS.getCode()))
				.andExpect(jsonPath("$.message").value(ErrorType.USER_ALREADY_EXISTS.getMessage()));
		}

		@Test
		@DisplayName("[POST /api/v1/users] 비밀번호 형식 오류 (8자 미만) -> 400 Bad Request. "
			+ "에러 코드: INVALID_PASSWORD_FORMAT")
		void signUpFailInvalidPasswordFormat() throws Exception {
			// Arrange
			UserSignUpRequest request = new UserSignUpRequest(
				"testuser01",
				"short",
				"홍길동",
				LocalDate.of(1990, 1, 15),
				"test@example.com"
			);

			// Act & Assert
			mockMvc.perform(post("/api/v1/users")
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(ErrorType.INVALID_PASSWORD_FORMAT.getCode()));
		}

		@Test
		@DisplayName("[POST /api/v1/users] 비밀번호에 생년월일 포함 -> 400 Bad Request. "
			+ "에러 코드: PASSWORD_CONTAINS_BIRTHDAY")
		void signUpFailPasswordContainsBirthday() throws Exception {
			// Arrange
			UserSignUpRequest request = new UserSignUpRequest(
				"testuser01",
				"Aa19900115!",
				"홍길동",
				LocalDate.of(1990, 1, 15),
				"test@example.com"
			);

			// Act & Assert
			mockMvc.perform(post("/api/v1/users")
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(ErrorType.PASSWORD_CONTAINS_BIRTHDAY.getCode()));
		}

		@Test
		@DisplayName("[POST /api/v1/users] 로그인 ID 형식 오류 (특수문자 포함) -> 400 Bad Request. "
			+ "에러 코드: INVALID_LOGIN_ID_FORMAT")
		void signUpFailInvalidLoginIdFormat() throws Exception {
			// Arrange
			UserSignUpRequest request = new UserSignUpRequest(
				"test_user!",
				"Test1234!",
				"홍길동",
				LocalDate.of(1990, 1, 15),
				"test@example.com"
			);

			// Act & Assert
			mockMvc.perform(post("/api/v1/users")
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(ErrorType.INVALID_LOGIN_ID_FORMAT.getCode()));
		}

		@Test
		@DisplayName("[POST /api/v1/users] 이메일 형식 오류 -> 400 Bad Request. "
			+ "에러 코드: INVALID_EMAIL_FORMAT")
		void signUpFailInvalidEmailFormat() throws Exception {
			// Arrange
			UserSignUpRequest request = new UserSignUpRequest(
				"testuser01",
				"Test1234!",
				"홍길동",
				LocalDate.of(1990, 1, 15),
				"invalid-email"
			);

			// Act & Assert
			mockMvc.perform(post("/api/v1/users")
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(ErrorType.INVALID_EMAIL_FORMAT.getCode()));
		}

		@Test
		@DisplayName("[POST /api/v1/users] 필수 필드 누락 (birthday, email) -> 400 Bad Request. "
			+ "에러 코드: BAD_REQUEST")
		void signUpFailMissingRequiredFields() throws Exception {
			// Arrange
			String requestJson = """
				{
					"loginId": "testuser01",
					"password": "Test1234!",
					"name": "홍길동"
				}
				""";

			// Act & Assert
			mockMvc.perform(post("/api/v1/users")
					.contentType(MediaType.APPLICATION_JSON)
					.content(requestJson))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(ErrorType.BAD_REQUEST.getCode()));
		}

		@Test
		@DisplayName("[POST /api/v1/users] 이름 형식 오류 (숫자 포함) -> 400 Bad Request. "
			+ "에러 코드: INVALID_NAME_FORMAT")
		void signUpFailInvalidNameFormat() throws Exception {
			// Arrange
			UserSignUpRequest request = new UserSignUpRequest(
				"testuser01",
				"Test1234!",
				"홍길동123",
				LocalDate.of(1990, 1, 15),
				"test@example.com"
			);

			// Act & Assert
			mockMvc.perform(post("/api/v1/users")
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(ErrorType.INVALID_NAME_FORMAT.getCode()));
		}

		@Test
		@DisplayName("[POST /api/v1/users] 로그인ID 앞뒤 공백 및 대문자 정규화 -> 201 Created. "
			+ "loginId '  TestUser01  ' -> 'testuser01'로 저장")
		void signUpNormalizesLoginId() throws Exception {
			// Arrange
			UserSignUpRequest request = new UserSignUpRequest(
				"  TestUser01  ",
				"Test1234!",
				"홍길동",
				LocalDate.of(1990, 1, 15),
				"test@example.com"
			);

			// Act & Assert
			mockMvc.perform(post("/api/v1/users")
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.loginId").value("testuser01"));
		}

		@Test
		@DisplayName("[POST /api/v1/users] 정규화 후 중복 loginId -> 409 Conflict. "
			+ "기존 'testuser01' 존재 시 'TestUser01' 가입 시도 -> 중복")
		void signUpFailDuplicateNormalizedLoginId() throws Exception {
			// Arrange - 먼저 testuser01 가입
			signUpUser("testuser01", "Test1234!", "홍길동",
				LocalDate.of(1990, 1, 15), "test@example.com");

			// 대문자 변형으로 가입 시도
			UserSignUpRequest request = new UserSignUpRequest(
				"TestUser01",
				"Test1234!",
				"김철수",
				LocalDate.of(1991, 2, 20),
				"test2@example.com"
			);

			// Act & Assert
			mockMvc.perform(post("/api/v1/users")
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value(ErrorType.USER_ALREADY_EXISTS.getCode()));
		}

		@Test
		@DisplayName("[POST /api/v1/users] 비밀번호에 공백 포함 -> 400 Bad Request. "
			+ "에러 코드: INVALID_PASSWORD_FORMAT")
		void signUpFailPasswordContainsWhitespace() throws Exception {
			// Arrange
			UserSignUpRequest request = new UserSignUpRequest(
				"testuser01",
				"Test 1234!",
				"홍길동",
				LocalDate.of(1990, 1, 15),
				"test@example.com"
			);

			// Act & Assert
			mockMvc.perform(post("/api/v1/users")
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(ErrorType.INVALID_PASSWORD_FORMAT.getCode()));
		}

		@Test
		@DisplayName("[POST /api/v1/users] 생년월일 오늘 당일 -> 400 Bad Request. "
			+ "에러 코드: INVALID_BIRTHDAY")
		void signUpFailBirthdayIsToday() throws Exception {
			// Arrange
			UserSignUpRequest request = new UserSignUpRequest(
				"testuser01",
				"Test1234!",
				"홍길동",
				LocalDate.now(),
				"test@example.com"
			);

			// Act & Assert
			mockMvc.perform(post("/api/v1/users")
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(ErrorType.INVALID_BIRTHDAY.getCode()));
		}

		@Test
		@DisplayName("[POST /api/v1/users] 공백 포함 영문 이름 -> 201 Created. "
			+ "'Hong Gildong' 허용")
		void signUpSuccessNameWithSpace() throws Exception {
			// Arrange
			UserSignUpRequest request = new UserSignUpRequest(
				"testuser01",
				"Test1234!",
				"Hong Gildong",
				LocalDate.of(1990, 1, 15),
				"test@example.com"
			);

			// Act & Assert
			mockMvc.perform(post("/api/v1/users")
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.name").value("Hong Gildong"));
		}

		@Test
		@DisplayName("[POST /api/v1/users] 비밀번호에 생년월일(YYYY-MM-DD) 포함 -> 400 Bad Request. "
			+ "에러 코드: PASSWORD_CONTAINS_BIRTHDAY")
		void signUpFailPasswordContainsBirthdayDash() throws Exception {
			// Arrange
			UserSignUpRequest request = new UserSignUpRequest(
				"testuser01",
				"Aa1990-01-15!",
				"홍길동",
				LocalDate.of(1990, 1, 15),
				"test@example.com"
			);

			// Act & Assert
			mockMvc.perform(post("/api/v1/users")
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(ErrorType.PASSWORD_CONTAINS_BIRTHDAY.getCode()));
		}

		@Test
		@DisplayName("[POST /api/v1/users] 생년월일 미래 날짜 -> 400 Bad Request. "
			+ "에러 코드: INVALID_BIRTHDAY")
		void signUpFailFutureBirthday() throws Exception {
			// Arrange
			UserSignUpRequest request = new UserSignUpRequest(
				"testuser01",
				"Test1234!",
				"홍길동",
				LocalDate.now().plusDays(1),
				"test@example.com"
			);

			// Act & Assert
			mockMvc.perform(post("/api/v1/users")
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(ErrorType.INVALID_BIRTHDAY.getCode()));
		}
	}

	private void signUpUser(String loginId, String password, String name,
							LocalDate birthday, String email) throws Exception {
		UserSignUpRequest request = new UserSignUpRequest(loginId, password, name, birthday, email);
		mockMvc.perform(post("/api/v1/users")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isCreated());
	}

	@Nested
	@DisplayName("GET /api/v1/users/me - 내 정보 조회")
	class GetMeTest {

		@Test
		@DisplayName("[GET /api/v1/users/me] 유효한 인증 헤더 -> 200 OK. loginId, maskedName, birthday, email 포함")
		void getMeSuccess() throws Exception {
			// Arrange
			signUpUser("testuser01", "Test1234!", "홍길동",
				LocalDate.of(1990, 1, 15), "test@example.com");

			// Act & Assert
			mockMvc.perform(get("/api/v1/users/me")
					.header("X-Loopers-LoginId", "testuser01")
					.header("X-Loopers-LoginPw", "Test1234!"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.loginId").value("testuser01"))
				.andExpect(jsonPath("$.name").value("홍길*"))
				.andExpect(jsonPath("$.birthday").value("1990-01-15"))
				.andExpect(jsonPath("$.email").value("test@example.com"));
		}

		@Test
		@DisplayName("[GET /api/v1/users/me] 응답에 password 미포함. 민감정보 누출 방지")
		void getMeResponseDoesNotContainPassword() throws Exception {
			// Arrange
			signUpUser("testuser01", "Test1234!", "홍길동",
				LocalDate.of(1990, 1, 15), "test@example.com");

			// Act & Assert
			mockMvc.perform(get("/api/v1/users/me")
					.header("X-Loopers-LoginId", "testuser01")
					.header("X-Loopers-LoginPw", "Test1234!"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", not(hasKey("password"))));
		}

		@Test
		@DisplayName("[GET /api/v1/users/me] X-Loopers-LoginId 누락 -> 401 Unauthorized")
		void getMeFailWhenLoginIdHeaderMissing() throws Exception {
			// Act & Assert
			mockMvc.perform(get("/api/v1/users/me")
					.header("X-Loopers-LoginPw", "Test1234!"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(ErrorType.UNAUTHORIZED.getCode()));
		}

		@Test
		@DisplayName("[GET /api/v1/users/me] X-Loopers-LoginPw 누락 -> 401 Unauthorized")
		void getMeFailWhenPasswordHeaderMissing() throws Exception {
			// Act & Assert
			mockMvc.perform(get("/api/v1/users/me")
					.header("X-Loopers-LoginId", "testuser01"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(ErrorType.UNAUTHORIZED.getCode()));
		}

		@Test
		@DisplayName("[GET /api/v1/users/me] 존재하지 않는 loginId -> 401 Unauthorized")
		void getMeFailWhenUserNotFound() throws Exception {
			// Act & Assert
			mockMvc.perform(get("/api/v1/users/me")
					.header("X-Loopers-LoginId", "nonexistent")
					.header("X-Loopers-LoginPw", "Test1234!"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(ErrorType.UNAUTHORIZED.getCode()));
		}

		@Test
		@DisplayName("[GET /api/v1/users/me] 비밀번호 불일치 -> 401 Unauthorized")
		void getMeFailWhenPasswordNotMatch() throws Exception {
			// Arrange
			signUpUser("testuser01", "Test1234!", "홍길동",
				LocalDate.of(1990, 1, 15), "test@example.com");

			// Act & Assert
			mockMvc.perform(get("/api/v1/users/me")
					.header("X-Loopers-LoginId", "testuser01")
					.header("X-Loopers-LoginPw", "WrongPass1!"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(ErrorType.UNAUTHORIZED.getCode()));
		}

		@Test
		@DisplayName("[GET /api/v1/users/me] loginId 앞뒤 공백 -> trim 후 정상 조회")
		void getMeSuccessWithTrimmedLoginId() throws Exception {
			// Arrange
			signUpUser("testuser01", "Test1234!", "홍길동",
				LocalDate.of(1990, 1, 15), "test@example.com");

			// Act & Assert
			mockMvc.perform(get("/api/v1/users/me")
					.header("X-Loopers-LoginId", "  testuser01  ")
					.header("X-Loopers-LoginPw", "Test1234!"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.loginId").value("testuser01"));
		}

		@Test
		@DisplayName("[GET /api/v1/users/me] 이름 1자 사용자 -> *로 마스킹")
		void getMeNameMasking1Char() throws Exception {
			// Arrange
			signUpUser("testuser02", "Test1234!", "김",
				LocalDate.of(1990, 1, 15), "test2@example.com");

			// Act & Assert
			mockMvc.perform(get("/api/v1/users/me")
					.header("X-Loopers-LoginId", "testuser02")
					.header("X-Loopers-LoginPw", "Test1234!"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("*"));
		}
	}

	@Nested
	@DisplayName("PATCH /api/v1/users/me/password - 비밀번호 변경")
	class ChangePasswordTest {

		@Test
		@DisplayName("[PATCH /api/v1/users/me/password] 유효한 비밀번호 변경 요청 -> 200 OK. "
			+ "변경 후 새 비밀번호로 인증 성공")
		void changePasswordSuccess() throws Exception {
			// Arrange
			signUpUser("testuser01", "Test1234!", "홍길동",
				LocalDate.of(1990, 1, 15), "test@example.com");

			String requestJson = """
				{
					"currentPassword": "Test1234!",
					"newPassword": "NewPass1234!"
				}
				""";

			// Act
			mockMvc.perform(patch("/api/v1/users/me/password")
					.header("X-Loopers-LoginId", "testuser01")
					.header("X-Loopers-LoginPw", "Test1234!")
					.contentType(MediaType.APPLICATION_JSON)
					.content(requestJson))
				.andExpect(status().isOk());

			// Assert - 새 비밀번호로 인증 성공
			mockMvc.perform(get("/api/v1/users/me")
					.header("X-Loopers-LoginId", "testuser01")
					.header("X-Loopers-LoginPw", "NewPass1234!"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.loginId").value("testuser01"));
		}

		@Test
		@DisplayName("[PATCH /api/v1/users/me/password] X-Loopers-LoginId 헤더 누락 -> 401 Unauthorized")
		void failWhenLoginIdHeaderMissing() throws Exception {
			// Arrange
			String requestJson = """
				{
					"currentPassword": "Test1234!",
					"newPassword": "NewPass1234!"
				}
				""";

			// Act & Assert
			mockMvc.perform(patch("/api/v1/users/me/password")
					.header("X-Loopers-LoginPw", "Test1234!")
					.contentType(MediaType.APPLICATION_JSON)
					.content(requestJson))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(ErrorType.UNAUTHORIZED.getCode()));
		}

		@Test
		@DisplayName("[PATCH /api/v1/users/me/password] X-Loopers-LoginPw 헤더 누락 -> 401 Unauthorized")
		void failWhenPasswordHeaderMissing() throws Exception {
			// Arrange
			String requestJson = """
				{
					"currentPassword": "Test1234!",
					"newPassword": "NewPass1234!"
				}
				""";

			// Act & Assert
			mockMvc.perform(patch("/api/v1/users/me/password")
					.header("X-Loopers-LoginId", "testuser01")
					.contentType(MediaType.APPLICATION_JSON)
					.content(requestJson))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(ErrorType.UNAUTHORIZED.getCode()));
		}

		@Test
		@DisplayName("[PATCH /api/v1/users/me/password] 헤더 비밀번호 불일치 -> 401 Unauthorized")
		void failWhenHeaderPasswordNotMatch() throws Exception {
			// Arrange
			signUpUser("testuser01", "Test1234!", "홍길동",
				LocalDate.of(1990, 1, 15), "test@example.com");

			String requestJson = """
				{
					"currentPassword": "Test1234!",
					"newPassword": "NewPass1234!"
				}
				""";

			// Act & Assert
			mockMvc.perform(patch("/api/v1/users/me/password")
					.header("X-Loopers-LoginId", "testuser01")
					.header("X-Loopers-LoginPw", "WrongPass1!")
					.contentType(MediaType.APPLICATION_JSON)
					.content(requestJson))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(ErrorType.UNAUTHORIZED.getCode()));
		}

		@Test
		@DisplayName("[PATCH /api/v1/users/me/password] currentPassword 불일치 -> 401 Unauthorized")
		void failWhenCurrentPasswordNotMatch() throws Exception {
			// Arrange
			signUpUser("testuser01", "Test1234!", "홍길동",
				LocalDate.of(1990, 1, 15), "test@example.com");

			String requestJson = """
				{
					"currentPassword": "WrongCurrent1!",
					"newPassword": "NewPass1234!"
				}
				""";

			// Act & Assert
			mockMvc.perform(patch("/api/v1/users/me/password")
					.header("X-Loopers-LoginId", "testuser01")
					.header("X-Loopers-LoginPw", "Test1234!")
					.contentType(MediaType.APPLICATION_JSON)
					.content(requestJson))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(ErrorType.UNAUTHORIZED.getCode()));
		}

		@Test
		@DisplayName("[PATCH /api/v1/users/me/password] newPassword == currentPassword -> 400 Bad Request. "
			+ "에러 코드: PASSWORD_SAME_AS_CURRENT")
		void failWhenNewPasswordSameAsCurrent() throws Exception {
			// Arrange
			signUpUser("testuser01", "Test1234!", "홍길동",
				LocalDate.of(1990, 1, 15), "test@example.com");

			String requestJson = """
				{
					"currentPassword": "Test1234!",
					"newPassword": "Test1234!"
				}
				""";

			// Act & Assert
			mockMvc.perform(patch("/api/v1/users/me/password")
					.header("X-Loopers-LoginId", "testuser01")
					.header("X-Loopers-LoginPw", "Test1234!")
					.contentType(MediaType.APPLICATION_JSON)
					.content(requestJson))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(ErrorType.PASSWORD_SAME_AS_CURRENT.getCode()));
		}

		@Test
		@DisplayName("[PATCH /api/v1/users/me/password] newPassword 형식 오류 -> 400 Bad Request. "
			+ "에러 코드: INVALID_PASSWORD_FORMAT")
		void failWhenNewPasswordFormatInvalid() throws Exception {
			// Arrange
			signUpUser("testuser01", "Test1234!", "홍길동",
				LocalDate.of(1990, 1, 15), "test@example.com");

			String requestJson = """
				{
					"currentPassword": "Test1234!",
					"newPassword": "short"
				}
				""";

			// Act & Assert
			mockMvc.perform(patch("/api/v1/users/me/password")
					.header("X-Loopers-LoginId", "testuser01")
					.header("X-Loopers-LoginPw", "Test1234!")
					.contentType(MediaType.APPLICATION_JSON)
					.content(requestJson))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(ErrorType.INVALID_PASSWORD_FORMAT.getCode()));
		}

		@Test
		@DisplayName("[PATCH /api/v1/users/me/password] newPassword에 생년월일 포함 -> 400 Bad Request. "
			+ "에러 코드: PASSWORD_CONTAINS_BIRTHDAY")
		void failWhenNewPasswordContainsBirthday() throws Exception {
			// Arrange
			signUpUser("testuser01", "Test1234!", "홍길동",
				LocalDate.of(1990, 1, 15), "test@example.com");

			String requestJson = """
				{
					"currentPassword": "Test1234!",
					"newPassword": "Aa19900115!"
				}
				""";

			// Act & Assert
			mockMvc.perform(patch("/api/v1/users/me/password")
					.header("X-Loopers-LoginId", "testuser01")
					.header("X-Loopers-LoginPw", "Test1234!")
					.contentType(MediaType.APPLICATION_JSON)
					.content(requestJson))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(ErrorType.PASSWORD_CONTAINS_BIRTHDAY.getCode()));
		}

		@Test
		@DisplayName("[PATCH /api/v1/users/me/password] 필수 필드 누락 (newPassword) -> 400 Bad Request")
		void failWhenRequiredFieldMissing() throws Exception {
			// Arrange
			String requestJson = """
				{
					"currentPassword": "Test1234!"
				}
				""";

			// Act & Assert
			mockMvc.perform(patch("/api/v1/users/me/password")
					.header("X-Loopers-LoginId", "testuser01")
					.header("X-Loopers-LoginPw", "Test1234!")
					.contentType(MediaType.APPLICATION_JSON)
					.content(requestJson))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(ErrorType.BAD_REQUEST.getCode()));
		}
	}
}
