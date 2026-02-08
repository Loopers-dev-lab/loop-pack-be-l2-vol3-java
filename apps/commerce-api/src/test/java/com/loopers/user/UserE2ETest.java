package com.loopers.user;

import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.user.dto.ChangePasswordRequest;
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
import static com.loopers.user.controller.UserController.LOGIN_PW_HEADER;

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

    @Test
    void 비밀번호_변경_API_요청시_200_OK_반환() {
        // given - 사용자 생성
        String loginId = "pwchg" + (System.currentTimeMillis() % 10000);
        String currentPassword = "Password1!";
        String newPassword = "NewPassword2@";

        CreateUserRequest createRequest = new CreateUserRequest(
                loginId, currentPassword, "홍길동", "1990-01-01", "test@example.com"
        );
        ResponseEntity<CreateUserResponse> createResponse = restTemplate.postForEntity("/api/v1/users", createRequest, CreateUserResponse.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // when - 비밀번호 변경
        HttpHeaders headers = new HttpHeaders();
        headers.set(LOGIN_ID_HEADER, loginId);
        headers.set(LOGIN_PW_HEADER, currentPassword);
        headers.setContentType(MediaType.APPLICATION_JSON);

        ChangePasswordRequest changeRequest = new ChangePasswordRequest(newPassword);
        HttpEntity<ChangePasswordRequest> entity = new HttpEntity<>(changeRequest, headers);

        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/v1/users/password",
                HttpMethod.PATCH,
                entity,
                Void.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void 비밀번호_변경시_기존_비밀번호가_일치하지_않으면_401_Unauthorized_반환() {
        // given - 사용자 생성
        String loginId = "pwfail" + (System.currentTimeMillis() % 1000);
        String currentPassword = "Password1!";
        String wrongPassword = "WrongPassword!";
        String newPassword = "NewPassword2@";

        CreateUserRequest createRequest = new CreateUserRequest(
                loginId, currentPassword, "홍길동", "1990-01-01", "test@example.com"
        );
        ResponseEntity<CreateUserResponse> createResponse = restTemplate.postForEntity("/api/v1/users", createRequest, CreateUserResponse.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // when - 잘못된 비밀번호로 변경 시도
        HttpHeaders headers = new HttpHeaders();
        headers.set(LOGIN_ID_HEADER, loginId);
        headers.set(LOGIN_PW_HEADER, wrongPassword);
        headers.setContentType(MediaType.APPLICATION_JSON);

        ChangePasswordRequest changeRequest = new ChangePasswordRequest(newPassword);
        HttpEntity<ChangePasswordRequest> entity = new HttpEntity<>(changeRequest, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/users/password",
                HttpMethod.PATCH,
                entity,
                String.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void 비밀번호_변경시_새_비밀번호가_기존과_동일하면_400_Bad_Request_반환() {
        // given - 사용자 생성
        String loginId = "pwsame" + (System.currentTimeMillis() % 1000);
        String currentPassword = "Password1!";

        CreateUserRequest createRequest = new CreateUserRequest(
                loginId, currentPassword, "홍길동", "1990-01-01", "test@example.com"
        );
        ResponseEntity<CreateUserResponse> createResponse = restTemplate.postForEntity("/api/v1/users", createRequest, CreateUserResponse.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // when - 동일한 비밀번호로 변경 시도
        HttpHeaders headers = new HttpHeaders();
        headers.set(LOGIN_ID_HEADER, loginId);
        headers.set(LOGIN_PW_HEADER, currentPassword);
        headers.setContentType(MediaType.APPLICATION_JSON);

        ChangePasswordRequest changeRequest = new ChangePasswordRequest(currentPassword);
        HttpEntity<ChangePasswordRequest> entity = new HttpEntity<>(changeRequest, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/users/password",
                HttpMethod.PATCH,
                entity,
                String.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void 비밀번호_변경시_8자_미만_규칙_위반하면_400_Bad_Request_반환() {
        // given - 사용자 생성
        String loginId = "pwrule" + (System.currentTimeMillis() % 1000);
        String currentPassword = "Password1!";
        String invalidPassword = "short";  // 8자 미만

        CreateUserRequest createRequest = new CreateUserRequest(
                loginId, currentPassword, "홍길동", "1990-01-01", "test@example.com"
        );
        ResponseEntity<CreateUserResponse> createResponse = restTemplate.postForEntity("/api/v1/users", createRequest, CreateUserResponse.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // when - 규칙 위반 비밀번호로 변경 시도
        HttpHeaders headers = new HttpHeaders();
        headers.set(LOGIN_ID_HEADER, loginId);
        headers.set(LOGIN_PW_HEADER, currentPassword);
        headers.setContentType(MediaType.APPLICATION_JSON);

        ChangePasswordRequest changeRequest = new ChangePasswordRequest(invalidPassword);
        HttpEntity<ChangePasswordRequest> entity = new HttpEntity<>(changeRequest, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/users/password",
                HttpMethod.PATCH,
                entity,
                String.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
