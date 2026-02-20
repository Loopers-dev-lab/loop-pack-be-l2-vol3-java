package com.loopers.interfaces.api.user.v1;

import static com.loopers.interfaces.api.user.v1.UserSteps.signUp;
import static com.loopers.support.E2ETestHelper.assertErrorResponse;
import static com.loopers.support.E2ETestHelper.userAuthHeaders;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.loopers.domain.user.UserName;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.user.v1.UserV1Dto.MeResponse;
import com.loopers.interfaces.api.user.v1.UserV1Dto.SignUpResponse;
import com.loopers.support.BaseE2ETest;
import com.loopers.support.error.ErrorType;

class UserV1ApiE2ETest extends BaseE2ETest {

    private static final String SIGNUP_ENDPOINT = "/api/v1/users";

    private UserV1Dto.SignUpRequest signUpRequest;
    private HttpHeaders headers;

    @BeforeEach
    void setUp() {
        signUpRequest = new UserV1Dto.SignUpRequest(
                "testuser1",
                "Password1!",
                "홍길동",
                "1990-01-15",
                "test@example.com"
        );
        signUp(testRestTemplate, signUpRequest);

        headers = userAuthHeaders(signUpRequest.loginId(), signUpRequest.password());
    }

    @DisplayName("POST /api/v1/users")
    @Nested
    class SignUp {

        @DisplayName("유효한 회원 정보를 입력하면, 회원가입에 성공한다.")
        @Test
        void signUp_success_whenValidInput() {
            // arrange
            UserV1Dto.SignUpRequest request = new UserV1Dto.SignUpRequest(
                    "newuser99",
                    "SecurePass9!",
                    "박신규",
                    "1995-06-20",
                    "newuser@example.com"
            );

            // act
            ParameterizedTypeReference<ApiResponse<SignUpResponse>> responseType = new ParameterizedTypeReference<>() {
            };
            ResponseEntity<ApiResponse<UserV1Dto.SignUpResponse>> response =
                    testRestTemplate.exchange(SIGNUP_ENDPOINT, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                    () -> assertThat(response.getBody()).isNotNull(),
                    () -> assertThat(response.getBody().data().loginId()).isEqualTo(request.loginId()),
                    () -> assertThat(response.getBody().data().name()).isEqualTo(new UserName(request.name()).masked()),
                    () -> assertThat(response.getBody().data().email()).isEqualTo(request.email())
            );
        }

        @DisplayName("이미 가입된 로그인 ID로 가입하면, DUPLICATE_LOGIN_ID 에러 응답을 받는다.")
        @Test
        void signUp_duplicateLoginId_whenLoginIdAlreadyExists() {
            // arrange
            UserV1Dto.SignUpRequest duplicateRequest = new UserV1Dto.SignUpRequest(
                    "testuser1",
                    "Password2@",
                    "김철수",
                    "1985-05-20",
                    "other@example.com"
            );

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.SignUpResponse>> responseType = new ParameterizedTypeReference<>() {
            };
            ResponseEntity<ApiResponse<UserV1Dto.SignUpResponse>> response =
                    testRestTemplate.exchange(SIGNUP_ENDPOINT, HttpMethod.POST, new HttpEntity<>(duplicateRequest), responseType);

            // assert
            assertErrorResponse(response, HttpStatus.BAD_REQUEST, ErrorType.DUPLICATE_LOGIN_ID);
        }

        @DisplayName("동일한 로그인 ID로 동시에 가입 요청하면, 하나만 성공하고 나머지는 DUPLICATE_LOGIN_ID 에러 응답을 받는다.")
        @Test
        void signUp_onlyOneSucceeds_whenConcurrentRequestsWithSameLoginId() throws InterruptedException {
            // arrange
            int threadCount = 10;
            ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger duplicateCount = new AtomicInteger(0);

            // act
            for (int i = 0; i < threadCount; i++) {
                int index = i;
                executorService.execute(() -> {
                    try {
                        UserV1Dto.SignUpRequest request = new UserV1Dto.SignUpRequest(
                                "concurrent",
                                "Password" + index + "!",
                                "이름" + index,
                                "1990-01-01",
                                "email" + index + "@test.com"
                        );
                        ParameterizedTypeReference<ApiResponse<SignUpResponse>> responseType = new ParameterizedTypeReference<>() {
                        };
                        ResponseEntity<ApiResponse<SignUpResponse>> response =
                                testRestTemplate.exchange(SIGNUP_ENDPOINT, HttpMethod.POST, new HttpEntity<>(request), responseType);
                        if (response.getStatusCode() == HttpStatus.CREATED) {
                            successCount.incrementAndGet();
                        } else if (response.getBody() != null
                                && ErrorType.DUPLICATE_LOGIN_ID.getCode().equals(response.getBody().meta().errorCode())
                        ) {
                            duplicateCount.incrementAndGet();
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();
            executorService.shutdown();

            // assert
            assertAll(
                    () -> assertThat(successCount.get()).isEqualTo(1),
                    () -> assertThat(duplicateCount.get()).isEqualTo(threadCount - 1)
            );
        }

        @DisplayName("유효하지 않은 로그인 ID 형식이면, INVALID_LOGIN_ID_FORMAT 에러 응답을 받는다.")
        @Test
        void signUp_invalidLoginIdFormat_whenContainsSpecialCharacter() {
            // arrange
            UserV1Dto.SignUpRequest request = new UserV1Dto.SignUpRequest(
                    "test-user!",
                    "Password1!",
                    "홍길동",
                    "1990-01-15",
                    "test@example.com"
            );

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.SignUpResponse>> responseType = new ParameterizedTypeReference<>() {
            };
            ResponseEntity<ApiResponse<UserV1Dto.SignUpResponse>> response =
                    testRestTemplate.exchange(SIGNUP_ENDPOINT, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // assert
            assertErrorResponse(response, HttpStatus.BAD_REQUEST, ErrorType.INVALID_LOGIN_ID_FORMAT);
        }

        @DisplayName("유효하지 않은 이메일 형식이면, INVALID_EMAIL_FORMAT 에러 응답을 받는다.")
        @Test
        void signUp_invalidEmailFormat_whenFormatIsInvalid() {
            // arrange
            UserV1Dto.SignUpRequest request = new UserV1Dto.SignUpRequest(
                    "testuser2",
                    "Password1!",
                    "홍길동",
                    "1990-01-15",
                    "invalid-email"
            );

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.SignUpResponse>> responseType = new ParameterizedTypeReference<>() {
            };
            ResponseEntity<ApiResponse<UserV1Dto.SignUpResponse>> response =
                    testRestTemplate.exchange(SIGNUP_ENDPOINT, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // assert
            assertErrorResponse(response, HttpStatus.BAD_REQUEST, ErrorType.INVALID_EMAIL_FORMAT);
        }

        @DisplayName("비밀번호 길이가 유효하지 않으면, INVALID_PASSWORD_LENGTH 에러 응답을 받는다.")
        @Test
        void signUp_invalidPasswordLength_whenTooShort() {
            // arrange
            UserV1Dto.SignUpRequest request = new UserV1Dto.SignUpRequest(
                    "testuser2",
                    "short",
                    "홍길동",
                    "1990-01-15",
                    "test2@example.com"
            );

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.SignUpResponse>> responseType = new ParameterizedTypeReference<>() {
            };
            ResponseEntity<ApiResponse<UserV1Dto.SignUpResponse>> response =
                    testRestTemplate.exchange(SIGNUP_ENDPOINT, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // assert
            assertErrorResponse(response, HttpStatus.BAD_REQUEST, ErrorType.INVALID_PASSWORD_LENGTH);
        }

        @DisplayName("비밀번호에 생년월일이 포함되면, BIRTH_DATE_IN_PASSWORD_NOT_ALLOWED 에러 응답을 받는다.")
        @Test
        void signUp_birthDateInPasswordNotAllowed_whenPasswordContainsBirthDate() {
            // arrange
            UserV1Dto.SignUpRequest request = new UserV1Dto.SignUpRequest(
                    "testuser2",
                    "Pass19900115!",
                    "홍길동",
                    "1990-01-15",
                    "test2@example.com"
            );

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.SignUpResponse>> responseType = new ParameterizedTypeReference<>() {
            };
            ResponseEntity<ApiResponse<UserV1Dto.SignUpResponse>> response =
                    testRestTemplate.exchange(SIGNUP_ENDPOINT, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // assert
            assertErrorResponse(response, HttpStatus.BAD_REQUEST, ErrorType.BIRTH_DATE_IN_PASSWORD_NOT_ALLOWED);
        }

        @DisplayName("이름이 빈 값이면, BAD_REQUEST 에러 응답을 받는다.")
        @Test
        void signUp_badRequest_whenNameIsEmpty() {
            // arrange
            UserV1Dto.SignUpRequest request = new UserV1Dto.SignUpRequest(
                    "testuser2",
                    "Password1!",
                    "",
                    "1990-01-15",
                    "test2@example.com"
            );

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.SignUpResponse>> responseType = new ParameterizedTypeReference<>() {
            };
            ResponseEntity<ApiResponse<UserV1Dto.SignUpResponse>> response =
                    testRestTemplate.exchange(SIGNUP_ENDPOINT, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // assert
            assertErrorResponse(response, HttpStatus.BAD_REQUEST, ErrorType.BAD_REQUEST);
        }

        @DisplayName("유효하지 않은 생년월일 형식이면, INVALID_BIRTH_DATE_FORMAT 에러 응답을 받는다.")
        @Test
        void signUp_invalidBirthDateFormat_whenFormatIsInvalid() {
            // arrange
            UserV1Dto.SignUpRequest request = new UserV1Dto.SignUpRequest(
                    "testuser2",
                    "Password1!",
                    "홍길동",
                    "19900115",
                    "test2@example.com"
            );

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.SignUpResponse>> responseType = new ParameterizedTypeReference<>() {
            };
            ResponseEntity<ApiResponse<UserV1Dto.SignUpResponse>> response =
                    testRestTemplate.exchange(SIGNUP_ENDPOINT, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // assert
            assertErrorResponse(response, HttpStatus.BAD_REQUEST, ErrorType.INVALID_BIRTH_DATE_FORMAT);
        }
    }

    @DisplayName("GET /api/v1/users/me")
    @Nested
    class GetMyInfo {

        private static final String ME_ENDPOINT = "/api/v1/users/me";

        @DisplayName("인증된 사용자 정보를 조회하면, 마스킹된 이름과 함께 사용자 정보를 반환한다.")
        @Test
        void getMyInfo_returnsMaskedName_whenAuthenticated() {
            // act
            ParameterizedTypeReference<ApiResponse<MeResponse>> responseType = new ParameterizedTypeReference<>() {
            };
            ResponseEntity<ApiResponse<MeResponse>> response =
                    testRestTemplate.exchange(ME_ENDPOINT, HttpMethod.GET, new HttpEntity<>(headers), responseType);

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody()).isNotNull(),
                    () -> assertThat(response.getBody().data().loginId()).isEqualTo(signUpRequest.loginId()),
                    () -> assertThat(response.getBody().data().name()).isEqualTo(new UserName(signUpRequest.name()).masked()),
                    () -> assertThat(response.getBody().data().birthDate()).isEqualTo(signUpRequest.birthDate()),
                    () -> assertThat(response.getBody().data().email()).isEqualTo(signUpRequest.email())
            );
        }

        @DisplayName("인증 헤더가 없으면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        void getMyInfo_unauthorized_whenNoAuthHeader() {
            // act
            ParameterizedTypeReference<ApiResponse<MeResponse>> responseType = new ParameterizedTypeReference<>() {
            };
            ResponseEntity<ApiResponse<MeResponse>> response =
                    testRestTemplate.exchange(ME_ENDPOINT, HttpMethod.GET, null, responseType);

            // assert
            assertErrorResponse(response, HttpStatus.UNAUTHORIZED, ErrorType.UNAUTHORIZED);
        }

        @DisplayName("존재하지 않는 로그인 ID로 인증하면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        void getMyInfo_unauthorized_whenLoginIdNotFound() {
            // arrange
            HttpHeaders invalidHeaders = userAuthHeaders("nonexistent", "Password1!");

            // act
            ParameterizedTypeReference<ApiResponse<MeResponse>> responseType = new ParameterizedTypeReference<>() {
            };
            ResponseEntity<ApiResponse<MeResponse>> response =
                    testRestTemplate.exchange(ME_ENDPOINT, HttpMethod.GET, new HttpEntity<>(invalidHeaders), responseType);

            // assert
            assertErrorResponse(response, HttpStatus.UNAUTHORIZED, ErrorType.UNAUTHORIZED);
        }

        @DisplayName("비밀번호가 일치하지 않으면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        void getMyInfo_unauthorized_whenPasswordMismatch() {
            // arrange
            HttpHeaders invalidHeaders = userAuthHeaders(signUpRequest.loginId(), "WrongPassword1!");

            // act
            ParameterizedTypeReference<ApiResponse<MeResponse>> responseType = new ParameterizedTypeReference<>() {
            };
            ResponseEntity<ApiResponse<MeResponse>> response =
                    testRestTemplate.exchange(ME_ENDPOINT, HttpMethod.GET, new HttpEntity<>(invalidHeaders), responseType);

            // assert
            assertErrorResponse(response, HttpStatus.UNAUTHORIZED, ErrorType.UNAUTHORIZED);
        }
    }

    @DisplayName("PUT /api/v1/users/me/password")
    @Nested
    class UpdatePassword {

        private static final String UPDATE_PASSWORD_ENDPOINT = "/api/v1/users/me/password";

        @DisplayName("올바른 기존 비밀번호와 새 비밀번호를 입력하면, 비밀번호가 수정된다.")
        @Test
        void updatePassword_success_whenValidInput() {
            // arrange
            UserV1Dto.UpdatePasswordRequest updatePasswordRequest = new UserV1Dto.UpdatePasswordRequest(
                    "Password1!",
                    "NewPassword2@"
            );

            // act
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {
            };
            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(UPDATE_PASSWORD_ENDPOINT, HttpMethod.PUT, new HttpEntity<>(updatePasswordRequest, headers), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @DisplayName("인증 헤더가 없으면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        void updatePassword_unauthorized_whenNoAuthHeader() {
            // arrange
            UserV1Dto.UpdatePasswordRequest updatePasswordRequest = new UserV1Dto.UpdatePasswordRequest(
                    "Password1!",
                    "NewPassword2@"
            );

            // act
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {
            };
            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(UPDATE_PASSWORD_ENDPOINT, HttpMethod.PUT, new HttpEntity<>(updatePasswordRequest), responseType);

            // assert
            assertErrorResponse(response, HttpStatus.UNAUTHORIZED, ErrorType.UNAUTHORIZED);
        }

        @DisplayName("존재하지 않는 로그인 ID로 인증하면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        void updatePassword_unauthorized_whenLoginIdNotFound() {
            // arrange
            HttpHeaders invalidHeaders = userAuthHeaders("nonexistent", "Password1!");

            UserV1Dto.UpdatePasswordRequest updatePasswordRequest = new UserV1Dto.UpdatePasswordRequest(
                    "Password1!",
                    "NewPassword2@"
            );

            // act
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {
            };
            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(UPDATE_PASSWORD_ENDPOINT, HttpMethod.PUT, new HttpEntity<>(updatePasswordRequest, invalidHeaders), responseType);

            // assert
            assertErrorResponse(response, HttpStatus.UNAUTHORIZED, ErrorType.UNAUTHORIZED);
        }

        @DisplayName("비밀번호가 일치하지 않으면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        void updatePassword_unauthorized_whenPasswordMismatch() {
            // arrange
            HttpHeaders invalidHeaders = userAuthHeaders(signUpRequest.loginId(), "WrongPassword1!");

            UserV1Dto.UpdatePasswordRequest updatePasswordRequest = new UserV1Dto.UpdatePasswordRequest(
                    "Password1!",
                    "NewPassword2@"
            );

            // act
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {
            };
            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(UPDATE_PASSWORD_ENDPOINT, HttpMethod.PUT, new HttpEntity<>(updatePasswordRequest, invalidHeaders), responseType);

            // assert
            assertErrorResponse(response, HttpStatus.UNAUTHORIZED, ErrorType.UNAUTHORIZED);
        }

        @DisplayName("기존 비밀번호가 빈 값이거나 null이면, 400 BAD_REQUEST 응답을 받는다.")
        @ParameterizedTest
        @NullAndEmptySource
        void updatePassword_badRequest_whenOldPasswordIsBlankOrNull(String oldPassword) {
            // arrange
            UserV1Dto.UpdatePasswordRequest updatePasswordRequest = new UserV1Dto.UpdatePasswordRequest(
                    oldPassword,
                    "NewPassword2@"
            );

            // act
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {
            };
            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(UPDATE_PASSWORD_ENDPOINT, HttpMethod.PUT, new HttpEntity<>(updatePasswordRequest, headers), responseType);

            // assert
            assertErrorResponse(response, HttpStatus.BAD_REQUEST, ErrorType.BAD_REQUEST);
        }

        @DisplayName("새 비밀번호가 빈 값이거나 null이면, 400 BAD_REQUEST 응답을 받는다.")
        @ParameterizedTest
        @NullAndEmptySource
        void updatePassword_badRequest_whenNewPasswordIsBlankOrNull(String newPassword) {
            // arrange
            UserV1Dto.UpdatePasswordRequest updatePasswordRequest = new UserV1Dto.UpdatePasswordRequest(
                    "Password1!",
                    newPassword
            );

            // act
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {
            };
            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(UPDATE_PASSWORD_ENDPOINT, HttpMethod.PUT, new HttpEntity<>(updatePasswordRequest, headers), responseType);

            // assert
            assertErrorResponse(response, HttpStatus.BAD_REQUEST, ErrorType.BAD_REQUEST);
        }

        @DisplayName("기존 비밀번호가 일치하지 않으면, PASSWORD_MISMATCH 에러 응답을 받는다.")
        @Test
        void updatePassword_passwordMismatch_whenOldPasswordDoesNotMatch() {
            // arrange
            UserV1Dto.UpdatePasswordRequest updatePasswordRequest = new UserV1Dto.UpdatePasswordRequest(
                    "WrongPassword1!",
                    "NewPassword2@"
            );

            // act
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {
            };
            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(UPDATE_PASSWORD_ENDPOINT, HttpMethod.PUT, new HttpEntity<>(updatePasswordRequest, headers), responseType);

            // assert
            assertErrorResponse(response, HttpStatus.BAD_REQUEST, ErrorType.PASSWORD_MISMATCH);
        }

        @DisplayName("현재 비밀번호와 동일한 비밀번호로 수정하면, PASSWORD_REUSE_NOT_ALLOWED 에러 응답을 받는다.")
        @Test
        void updatePassword_passwordReuseNotAllowed_whenNewPasswordIsSameAsOld() {
            // arrange
            UserV1Dto.UpdatePasswordRequest updatePasswordRequest = new UserV1Dto.UpdatePasswordRequest(
                    "Password1!",
                    "Password1!"
            );

            // act
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {
            };
            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(UPDATE_PASSWORD_ENDPOINT, HttpMethod.PUT, new HttpEntity<>(updatePasswordRequest, headers), responseType);

            // assert
            assertErrorResponse(response, HttpStatus.BAD_REQUEST, ErrorType.PASSWORD_REUSE_NOT_ALLOWED);
        }

        @DisplayName("새 비밀번호에 생년월일이 포함되면, BIRTH_DATE_IN_PASSWORD_NOT_ALLOWED 에러 응답을 받는다.")
        @Test
        void updatePassword_birthDateInPasswordNotAllowed_whenNewPasswordContainsBirthDate() {
            // arrange
            UserV1Dto.UpdatePasswordRequest updatePasswordRequest = new UserV1Dto.UpdatePasswordRequest(
                    "Password1!",
                    "Pass19900115!"
            );

            // act
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {
            };
            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(UPDATE_PASSWORD_ENDPOINT, HttpMethod.PUT, new HttpEntity<>(updatePasswordRequest, headers), responseType);

            // assert
            assertErrorResponse(response, HttpStatus.BAD_REQUEST, ErrorType.BIRTH_DATE_IN_PASSWORD_NOT_ALLOWED);
        }
    }
}