package com.loopers.interfaces.api;

import com.loopers.domain.user.User;
import com.loopers.domain.user.UserFixture;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserV1ApiE2ETest {

    private static final String ENDPOINT_GET_MY_INFO = "/api/v1/users/me";
    private static final String ENDPOINT_UPDATE_PASSWORD = "/api/v1/users/me/password";

    private final TestRestTemplate testRestTemplate;
    private final UserJpaRepository userJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public UserV1ApiE2ETest(
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

    @DisplayName("GET /api/v1/users/me (내 정보 조회)")
    @Nested
    class GetMyInfo {

        @DisplayName("존재하는 사용자를 조회하면, 200 OK와 마스킹된 이름을 반환한다.")
        @Test
        void returnsOk_whenUserExists() {
            // arrange
            User savedUser = UserFixture.builder()
                                        .loginId("testUser123")
                                        .name("박자바")
                                        .build();
            userJpaRepository.save(savedUser);

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", savedUser.getLoginId());
            HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response =
                testRestTemplate.exchange(ENDPOINT_GET_MY_INFO, HttpMethod.GET, requestEntity, responseType);

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody()).isNotNull(),
                () -> assertThat(response.getBody().data().loginId()).isEqualTo("testUser123"),
                () -> assertThat(response.getBody().data().name()).isEqualTo("박자*")
            );
        }

        @DisplayName("존재하지 않는 사용자를 조회하면, 404 Not Found를 반환한다.")
        @Test
        void returnsNotFound_whenUserNotExists() {
            // arrange
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", "nonExistingId");
            HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response =
                testRestTemplate.exchange(ENDPOINT_GET_MY_INFO, HttpMethod.GET, requestEntity, responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("PATCH /api/v1/users/me/password (비밀번호 변경)")
    @Nested
    class UpdatePassword {

        private final BCryptPasswordEncoder bCryptPasswordEncoder = new BCryptPasswordEncoder();

        @DisplayName("유효한 비밀번호 변경 요청이면, 200 OK를 반환하고 비밀번호가 변경된다.")
        @Test
        void returnsOk_whenValidRequest() {
            // arrange
            String loginId = "testUser123";
            String oldEncodedPassword = bCryptPasswordEncoder.encode("OldPass1!");
            User savedUser = UserFixture.builder()
                    .loginId(loginId)
                    .password(oldEncodedPassword)
                    .build();
            userJpaRepository.save(savedUser);

            String newPassword = "NewPass1!";
            UserV1Dto.UpdatePasswordRequest request = new UserV1Dto.UpdatePasswordRequest(newPassword);

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", loginId);
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<UserV1Dto.UpdatePasswordRequest> requestEntity = new HttpEntity<>(request, headers);

            // act
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response =
                testRestTemplate.exchange(ENDPOINT_UPDATE_PASSWORD, HttpMethod.PATCH, requestEntity, responseType);

            // assert
            User updatedUser = userJpaRepository.findByLoginId(loginId).orElseThrow();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(bCryptPasswordEncoder.matches(newPassword, updatedUser.getPassword())).isTrue()
            );
        }

        @DisplayName("존재하지 않는 사용자의 비밀번호를 변경하면, 404 Not Found를 반환한다.")
        @Test
        void returnsNotFound_whenUserNotExists() {
            // arrange
            UserV1Dto.UpdatePasswordRequest request = new UserV1Dto.UpdatePasswordRequest("NewPass1!");

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", "nonExistingId");
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<UserV1Dto.UpdatePasswordRequest> requestEntity = new HttpEntity<>(request, headers);

            // act
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response =
                testRestTemplate.exchange(ENDPOINT_UPDATE_PASSWORD, HttpMethod.PATCH, requestEntity, responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("현재 비밀번호와 동일한 비밀번호로 변경하면, 400 Bad Request를 반환한다.")
        @Test
        void returnsBadRequest_whenSamePassword() {
            // arrange
            String loginId = "testUser123";
            String currentPassword = "SamePass1!";
            String encodedPassword = bCryptPasswordEncoder.encode(currentPassword);
            User savedUser = UserFixture.builder()
                    .loginId(loginId)
                    .password(encodedPassword)
                    .build();
            userJpaRepository.save(savedUser);

            UserV1Dto.UpdatePasswordRequest request = new UserV1Dto.UpdatePasswordRequest(currentPassword);

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", loginId);
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<UserV1Dto.UpdatePasswordRequest> requestEntity = new HttpEntity<>(request, headers);

            // act
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response =
                testRestTemplate.exchange(ENDPOINT_UPDATE_PASSWORD, HttpMethod.PATCH, requestEntity, responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }
}
