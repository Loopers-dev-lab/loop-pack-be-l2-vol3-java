package com.loopers.interfaces.api;

import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.interfaces.api.user.dto.UserV1Dto;
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
class SignUpV1ApiE2ETest {

    private static final String ENDPOINT_SIGN_UP = "/api/v1/users";

    private final TestRestTemplate testRestTemplate;
    private final UserJpaRepository userJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public SignUpV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        UserJpaRepository userJpaRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.userJpaRepository = userJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("POST /api/v1/users (회원가입)")
    @Nested
    class SignUp {
        @DisplayName("유효한 회원가입 요청이면, 201 Created 응답을 반환한다.")
        @Test
        void returnsCreated_whenValidRequest() {
            // arrange
            UserV1Dto.CreateRequest request = new UserV1Dto.CreateRequest(
                    "testUser123",
                    "ValidPass1!",
                    "박자바",
                    LocalDate.of(1990, 1, 15),
                    "test@example.com"
            );

            // act
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response =
                testRestTemplate.exchange(ENDPOINT_SIGN_UP, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                () -> assertThat(userJpaRepository.findByLoginId("testUser123")).isPresent()
            );
        }

        @DisplayName("loginId가 누락되면, 400 Bad Request 응답을 반환한다.")
        @Test
        void returnsBadRequest_whenLoginIdIsMissing() {
            // arrange
            UserV1Dto.CreateRequest request = new UserV1Dto.CreateRequest(
                    null,
                    "ValidPass1!",
                    "박자바",
                    LocalDate.of(1990, 1, 15),
                    "test@example.com"
            );

            // act
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response =
                testRestTemplate.exchange(ENDPOINT_SIGN_UP, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("이미 존재하는 loginId로 요청하면, 409 Conflict 응답을 반환한다.")
        @Test
        void returnsConflict_whenLoginIdAlreadyExists() {
            // arrange
            UserV1Dto.CreateRequest firstRequest = new UserV1Dto.CreateRequest(
                    "duplicateId",
                    "ValidPass1!",
                    "박자바",
                    LocalDate.of(1990, 1, 15),
                    "first@example.com"
            );

            testRestTemplate.exchange(ENDPOINT_SIGN_UP, HttpMethod.POST, new HttpEntity<>(firstRequest),
                new ParameterizedTypeReference<ApiResponse<Void>>() {});

            UserV1Dto.CreateRequest secondRequest = new UserV1Dto.CreateRequest(
                    "duplicateId",
                    "ValidPass2!",
                    "김자바",
                    LocalDate.of(1995, 5, 20),
                    "second@example.com"
            );

            // act
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response =
                testRestTemplate.exchange(ENDPOINT_SIGN_UP, HttpMethod.POST, new HttpEntity<>(secondRequest), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }
    }
}