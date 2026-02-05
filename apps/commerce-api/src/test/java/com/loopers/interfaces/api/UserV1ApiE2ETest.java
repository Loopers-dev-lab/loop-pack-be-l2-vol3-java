package com.loopers.interfaces.api;

import com.loopers.domain.user.PasswordEncoder;
import com.loopers.domain.user.UserModel;
import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.interfaces.api.user.UserV1Dto;
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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserV1ApiE2ETest {

    private static final String LOGIN_ID = "testuser1";
    private static final String RAW_PASSWORD = "Test1234!";
    private static final String NAME = "홍길동";
    private static final LocalDate BIRTH_DATE = LocalDate.of(1990, 1, 15);
    private static final String EMAIL = "test@example.com";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private UserModel createUser(String loginId, String rawPassword, String name, LocalDate birthDate, String email) {
        return userJpaRepository.save(new UserModel(
            loginId,
            passwordEncoder.encode(rawPassword),
            name,
            birthDate,
            email
        ));
    }

    @DisplayName("POST /user/signup")
    @Nested
    class Signup {

        @DisplayName("유효한 회원가입 정보가 주어지면, 201 Created와 회원 정보를 반환한다.")
        @Test
        void returns201_whenValidSignupRequest() {
            // arrange
            UserV1Dto.SignupRequest request = new UserV1Dto.SignupRequest(
                LOGIN_ID, RAW_PASSWORD, NAME, BIRTH_DATE, EMAIL
            );

            // act
            ResponseEntity<ApiResponse<UserV1Dto.SignupResponse>> response = testRestTemplate.exchange(
                "/user/signup",
                HttpMethod.POST,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<>() {
                }
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                () -> assertThat(response.getBody()).isNotNull(),
                () -> assertThat(response.getBody().data().loginId()).isEqualTo(LOGIN_ID),
                () -> assertThat(response.getBody().data().name()).isEqualTo(NAME),
                () -> assertThat(response.getBody().data().birthDate()).isEqualTo(BIRTH_DATE),
                () -> assertThat(response.getBody().data().email()).isEqualTo(EMAIL)
            );
        }

        @DisplayName("이미 존재하는 로그인 ID로 가입하면, 409 Conflict를 반환한다.")
        @Test
        void returns409_whenLoginIdAlreadyExists() {
            // arrange
            createUser("existinguser", RAW_PASSWORD, "기존유저", LocalDate.of(1990, 1, 1), "existing@test.com");

            UserV1Dto.SignupRequest request = new UserV1Dto.SignupRequest(
                "existinguser", RAW_PASSWORD, NAME, LocalDate.of(1995, 5, 5), "new@example.com"
            );

            // act
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                "/user/signup",
                HttpMethod.POST,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<>() {
                }
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @DisplayName("비밀번호에 생년월일이 포함되면, 400 Bad Request를 반환한다.")
        @Test
        void returns400_whenPasswordContainsBirthDate() {
            // arrange
            UserV1Dto.SignupRequest request = new UserV1Dto.SignupRequest(
                LOGIN_ID, "Pass19900115!", NAME, BIRTH_DATE, EMAIL
            );

            // act
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                "/user/signup",
                HttpMethod.POST,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<>() {
                }
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("비밀번호가 8자 미만이면, 400 Bad Request를 반환한다.")
        @Test
        void returns400_whenPasswordIsTooShort() {
            // arrange
            UserV1Dto.SignupRequest request = new UserV1Dto.SignupRequest(
                LOGIN_ID, "Short1!", NAME, BIRTH_DATE, EMAIL
            );

            // act
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                "/user/signup",
                HttpMethod.POST,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<>() {
                }
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("로그인 ID에 특수문자가 포함되면, 400 Bad Request를 반환한다.")
        @Test
        void returns400_whenLoginIdContainsSpecialCharacters() {
            // arrange
            UserV1Dto.SignupRequest request = new UserV1Dto.SignupRequest(
                "test@user", RAW_PASSWORD, NAME, BIRTH_DATE, EMAIL
            );

            // act
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                "/user/signup",
                HttpMethod.POST,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<>() {
                }
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("이메일 형식이 잘못되면, 400 Bad Request를 반환한다.")
        @Test
        void returns400_whenEmailFormatIsInvalid() {
            // arrange
            UserV1Dto.SignupRequest request = new UserV1Dto.SignupRequest(
                LOGIN_ID, RAW_PASSWORD, NAME, BIRTH_DATE, "invalid-email"
            );

            // act
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                "/user/signup",
                HttpMethod.POST,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<>() {
                }
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }
}