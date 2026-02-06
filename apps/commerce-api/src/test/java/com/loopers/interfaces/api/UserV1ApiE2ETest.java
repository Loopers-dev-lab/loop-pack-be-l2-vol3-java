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
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserV1ApiE2ETest {

    private static final String ENDPOINT_GET_MY_INFO = "/api/v1/users/me";

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
}
