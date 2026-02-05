package com.loopers.user;

import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.user.dto.CreateUserRequest;
import com.loopers.user.dto.CreateUserResponse;
import com.loopers.user.dto.GetMyInfoResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import static com.loopers.user.controller.UserController.LOGIN_ID_HEADER;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(MySqlTestContainersConfig.class)
@Transactional
public class UserE2ETest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void 회원가입_API_요청시_사용자가_생성되고_201_Created_반환() {
        // given
        CreateUserRequest request = new CreateUserRequest(
                "testuser",
                "Password1!",
                "홍길동",
                "1990-01-01",
                "test@example.com"
        );

        //실제 HTTP 요청
        ResponseEntity<CreateUserResponse> response = restTemplate.postForEntity(
                "/api/v1/users",
                request,
                CreateUserResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void 이미_존재하는_로그인ID로_회원가입시_409_Conflict_반환() {
        // given
        CreateUserRequest request = new CreateUserRequest(
                "dupuser",
                "Password1!",
                "홍길동",
                "1990-01-01",
                "test@example.com"
        );

        // 첫 번째 회원가입 (성공)
        restTemplate.postForEntity("/api/v1/users", request, CreateUserResponse.class);

        // when - 동일한 loginId로 두 번째 회원가입 시도
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/users",
                request,
                String.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void 내_정보_조회_API_요청시_마스킹된_이름이_포함된_사용자_정보와_200_OK_반환() {
        // given - 사용자 생성
        String loginId = "myinfouser";
        CreateUserRequest createRequest = new CreateUserRequest(
                loginId, "Password1!", "홍길동", "1990-01-01", "test@example.com"
        );
        restTemplate.postForEntity("/api/v1/users", createRequest, CreateUserResponse.class);

        // when - 내 정보 조회
        HttpHeaders headers = new HttpHeaders();
        headers.set(LOGIN_ID_HEADER, loginId);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<GetMyInfoResponse> response = restTemplate.exchange(
                "/api/v1/users/me",
                HttpMethod.GET,
                entity,
                GetMyInfoResponse.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().loginId()).isEqualTo(loginId);
        assertThat(response.getBody().name()).isEqualTo("홍길*");
        assertThat(response.getBody().birthDate()).isEqualTo("1990-01-01");
        assertThat(response.getBody().email()).isEqualTo("test@example.com");
    }

    @Test
    void 존재하지_않는_로그인ID로_내_정보_조회시_401_Unauthorized_반환() {
        // given - 존재하지 않는 로그인 ID
        HttpHeaders headers = new HttpHeaders();
        headers.set(LOGIN_ID_HEADER, "nonexistentuser");
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        // when
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/users/me",
                HttpMethod.GET,
                entity,
                String.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
