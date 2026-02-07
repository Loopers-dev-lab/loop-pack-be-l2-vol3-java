package com.loopers.interfaces.api;

import com.loopers.domain.member.Gender;
import com.loopers.domain.member.MemberRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class MemberV1ApiE2ETest {

    private static final String ENDPOINT_REGISTER = "/api/v1/members/register";
    private static final String ENDPOINT_ME = "/api/v1/members/me";
    private static final String ENDPOINT_CHANGE_PASSWORD = "/api/v1/members/me/password";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("POST /api/v1/members/register")
    @Nested
    class Register {
        @DisplayName("회원 가입이 성공할 경우, 생성된 유저 정보를 응답으로 반환한다.")
        @Test
        void successfulRegistration_returnsCreatedUserInfo() {
            // arrange
            MemberV1Dto.RegisterRequest request = new MemberV1Dto.RegisterRequest(
                "testuser1",
                "Test1234!",
                "test@example.com",
                "1995-05-20",
                "테스트유저",
                Gender.MALE
            );

            // act
            ParameterizedTypeReference<ApiResponse<MemberV1Dto.MemberResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<MemberV1Dto.MemberResponse>> response =
                testRestTemplate.exchange(ENDPOINT_REGISTER, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody()).isNotNull(),
                () -> assertThat(response.getBody().data()).isNotNull(),
                () -> assertThat(response.getBody().data().id()).isNotNull(),
                () -> assertThat(response.getBody().data().memberId()).isEqualTo("testuser1"),
                () -> assertThat(response.getBody().data().email()).isEqualTo("test@example.com"),
                () -> assertThat(response.getBody().data().birthDate()).isEqualTo("1995-05-20"),
                () -> assertThat(response.getBody().data().name()).isEqualTo("테스트유저"),
                () -> assertThat(response.getBody().data().gender()).isEqualTo(Gender.MALE)
            );
        }

        @DisplayName("회원 가입 시에 성별이 없을 경우, 400 Bad Request 응답을 반환한다.")
        @Test
        void missingGender_returnsBadRequest() {
            // arrange
            MemberV1Dto.RegisterRequest request = new MemberV1Dto.RegisterRequest(
                "testuser2",
                "Test1234!",
                "test2@example.com",
                "1995-05-20",
                "테스트유저2",
                null  // gender가 null
            );

            // act
            ParameterizedTypeReference<ApiResponse<MemberV1Dto.MemberResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<MemberV1Dto.MemberResponse>> response =
                testRestTemplate.exchange(ENDPOINT_REGISTER, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is4xxClientError(),
                    "Expected 4xx status but got: " + response.getStatusCode()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST)
            );
        }
    }

    @DisplayName("PATCH /api/v1/members/me/password")
    @Nested
    class ChangePassword {

        private void registerMember() {
            MemberV1Dto.RegisterRequest request = new MemberV1Dto.RegisterRequest(
                "testuser1", "Test1234!", "test@example.com",
                "1995-05-20", "테스트유저", Gender.MALE
            );
            testRestTemplate.exchange(
                ENDPOINT_REGISTER, HttpMethod.POST, new HttpEntity<>(request),
                new ParameterizedTypeReference<ApiResponse<MemberV1Dto.MemberResponse>>() {}
            );
        }

        @DisplayName("비밀번호 변경에 성공할 경우, 변경된 비밀번호로 내 정보 조회가 가능하다.")
        @Test
        void successfulChangePassword_allowsLoginWithNewPassword() {
            // arrange
            registerMember();
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", "testuser1");
            headers.set("X-Loopers-LoginPw", "Test1234!");

            MemberV1Dto.ChangePasswordRequest changeRequest = new MemberV1Dto.ChangePasswordRequest(
                "Test1234!", "NewPass1!"
            );

            // act - 비밀번호 변경
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> changeResponse =
                testRestTemplate.exchange(ENDPOINT_CHANGE_PASSWORD, HttpMethod.PATCH,
                    new HttpEntity<>(changeRequest, headers), responseType);

            // assert - 변경 성공
            assertTrue(changeResponse.getStatusCode().is2xxSuccessful());

            // assert - 새 비밀번호로 내 정보 조회 성공
            HttpHeaders newHeaders = new HttpHeaders();
            newHeaders.set("X-Loopers-LoginId", "testuser1");
            newHeaders.set("X-Loopers-LoginPw", "NewPass1!");

            ResponseEntity<ApiResponse<MemberV1Dto.MeResponse>> meResponse =
                testRestTemplate.exchange(ENDPOINT_ME, HttpMethod.GET,
                    new HttpEntity<>(newHeaders),
                    new ParameterizedTypeReference<>() {});

            assertThat(meResponse.getStatusCode().is2xxSuccessful()).isTrue();
        }

        @DisplayName("기존 비밀번호가 일치하지 않으면, 400 Bad Request 응답을 반환한다.")
        @Test
        void wrongCurrentPassword_returnsBadRequest() {
            // arrange
            registerMember();
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", "testuser1");
            headers.set("X-Loopers-LoginPw", "Test1234!");

            MemberV1Dto.ChangePasswordRequest changeRequest = new MemberV1Dto.ChangePasswordRequest(
                "WrongPass1!", "NewPass1!"
            );

            // act
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response =
                testRestTemplate.exchange(ENDPOINT_CHANGE_PASSWORD, HttpMethod.PATCH,
                    new HttpEntity<>(changeRequest, headers), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("새 비밀번호가 기존 비밀번호와 동일하면, 400 Bad Request 응답을 반환한다.")
        @Test
        void sameNewPassword_returnsBadRequest() {
            // arrange
            registerMember();
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", "testuser1");
            headers.set("X-Loopers-LoginPw", "Test1234!");

            MemberV1Dto.ChangePasswordRequest changeRequest = new MemberV1Dto.ChangePasswordRequest(
                "Test1234!", "Test1234!"
            );

            // act
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response =
                testRestTemplate.exchange(ENDPOINT_CHANGE_PASSWORD, HttpMethod.PATCH,
                    new HttpEntity<>(changeRequest, headers), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("GET /api/v1/members/me")
    @Nested
    class GetMe {

        @DisplayName("내 정보 조회에 성공할 경우, 해당하는 유저 정보를 응답으로 반환한다.")
        @Test
        void successfulGetMe_returnsMemberInfoWithMaskedName() {
            // arrange - 회원가입
            MemberV1Dto.RegisterRequest registerRequest = new MemberV1Dto.RegisterRequest(
                "testuser1",
                "Test1234!",
                "test@example.com",
                "1995-05-20",
                "테스트유저",
                Gender.MALE
            );
            testRestTemplate.exchange(
                ENDPOINT_REGISTER, HttpMethod.POST, new HttpEntity<>(registerRequest),
                new ParameterizedTypeReference<ApiResponse<MemberV1Dto.MemberResponse>>() {}
            );

            // arrange - 로그인 헤더 설정
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", "testuser1");
            headers.set("X-Loopers-LoginPw", "Test1234!");

            // act
            ParameterizedTypeReference<ApiResponse<MemberV1Dto.MeResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<MemberV1Dto.MeResponse>> response =
                testRestTemplate.exchange(ENDPOINT_ME, HttpMethod.GET, new HttpEntity<>(headers), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody()).isNotNull(),
                () -> assertThat(response.getBody().data()).isNotNull(),
                () -> assertThat(response.getBody().data().memberId()).isEqualTo("testuser1"),
                () -> assertThat(response.getBody().data().email()).isEqualTo("test@example.com"),
                () -> assertThat(response.getBody().data().birthDate()).isEqualTo("1995-05-20"),
                () -> assertThat(response.getBody().data().name()).isEqualTo("테스트유*")
            );
        }

        @DisplayName("존재하지 않는 ID 로 조회할 경우, 404 Not Found 응답을 반환한다.")
        @Test
        void nonExistentMember_returnsNotFound() {
            // arrange - 로그인 헤더에 존재하지 않는 ID 설정
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", "unknown99");
            headers.set("X-Loopers-LoginPw", "Test1234!");

            // act
            ParameterizedTypeReference<ApiResponse<MemberV1Dto.MeResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<MemberV1Dto.MeResponse>> response =
                testRestTemplate.exchange(ENDPOINT_ME, HttpMethod.GET, new HttpEntity<>(headers), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is4xxClientError()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND)
            );
        }
    }
}
