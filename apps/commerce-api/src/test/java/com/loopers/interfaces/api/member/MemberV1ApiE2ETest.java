package com.loopers.interfaces.api.member;

import com.loopers.interfaces.api.ApiResponse;
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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MemberV1ApiE2ETest {

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private ResponseEntity<ApiResponse<MemberV1Dto.SignUpResponse>> signUp(Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        return testRestTemplate.exchange(
            "/api/v1/members",
            HttpMethod.POST,
            request,
            new ParameterizedTypeReference<>() {}
        );
    }

    private Map<String, Object> validSignUpBody() {
        return Map.of(
            "loginId", "user1",
            "password", "Password1!",
            "name", "홍길동",
            "birthDate", "1990-01-15",
            "email", "test@example.com"
        );
    }

    @DisplayName("POST /api/v1/members (회원 가입)")
    @Nested
    class SignUp {

        @DisplayName("회원 가입이 성공할 경우, 생성된 유저 정보를 응답으로 반환한다")
        @Test
        void signUp_withValidRequest_returnsCreatedWithUserInfo() {
            // act
            ResponseEntity<ApiResponse<MemberV1Dto.SignUpResponse>> response = signUp(validSignUpBody());

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                () -> assertThat(response.getBody()).isNotNull(),
                () -> assertThat(response.getBody().data().loginId()).isEqualTo("user1"),
                () -> assertThat(response.getBody().data().name()).isEqualTo("홍길동"),
                () -> assertThat(response.getBody().data().email()).isEqualTo("test@example.com")
            );
        }
    }

    @DisplayName("GET /api/v1/members/me (내 정보 조회)")
    @Nested
    class GetMyInfo {

        @DisplayName("내 정보 조회에 성공할 경우, 해당하는 유저 정보를 응답으로 반환한다")
        @Test
        void getMyInfo_withValidAuth_returnsUserInfo() {
            // arrange
            signUp(validSignUpBody());

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", "user1");
            headers.set("X-Loopers-LoginPw", "Password1!");

            // act
            ResponseEntity<ApiResponse<MemberV1Dto.MyInfoResponse>> response = testRestTemplate.exchange(
                "/api/v1/members/me",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody()).isNotNull(),
                () -> assertThat(response.getBody().data().loginId()).isEqualTo("user1"),
                () -> assertThat(response.getBody().data().email()).isEqualTo("test@example.com")
            );
        }

        @DisplayName("존재하지 않는 ID로 조회할 경우, 401 Unauthorized")
        @Test
        void getMyInfo_withNonExistentId_returnsUnauthorized() {
            // arrange
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", "nobody");
            headers.set("X-Loopers-LoginPw", "Password1!");

            // act
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                "/api/v1/members/me",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }
}
