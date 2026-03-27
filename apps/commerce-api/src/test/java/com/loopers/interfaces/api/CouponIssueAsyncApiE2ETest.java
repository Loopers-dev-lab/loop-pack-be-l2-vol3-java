package com.loopers.interfaces.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponType;
import com.loopers.infrastructure.coupon.CouponJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import java.time.ZonedDateTime;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CouponIssueAsyncApiE2ETest {

    private static final String USERS_ENDPOINT = "/api/v1/users";
    private static final String ISSUE_ASYNC_ENDPOINT = "/api/v1/coupons";
    private static final String ISSUE_REQUESTS_ENDPOINT = "/api/v1/coupons/issue-requests";

    private static final String LOGIN_ID = "asyncuser";
    private static final String PASSWORD = "Async1234!";
    private static final String OTHER_LOGIN_ID = "otheruser";
    private static final String OTHER_PASSWORD = "Other1234!";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private CouponJpaRepository couponJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @BeforeEach
    void setUp() {
        registerUser(LOGIN_ID, PASSWORD, "비동기유저", "test1@example.com");
        registerUser(OTHER_LOGIN_ID, OTHER_PASSWORD, "다른유저", "test2@example.com");
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private void registerUser(String loginId, String password, String name, String email) {
        testRestTemplate.exchange(
            USERS_ENDPOINT, HttpMethod.POST,
            new HttpEntity<>(Map.of(
                "loginId", loginId,
                "password", password,
                "name", name,
                "birthDate", "19900101",
                "email", email
            )),
            new ParameterizedTypeReference<ApiResponse<Map<String, Object>>>() {}
        );
    }

    private HttpHeaders authHeaders(String loginId, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", loginId);
        headers.set("X-Loopers-LoginPw", password);
        return headers;
    }

    @DisplayName("POST /api/v1/coupons/{couponId}/issue-async (비동기 발급 요청)")
    @Nested
    class IssueAsync {

        @DisplayName("유효한 쿠폰에 요청하면, 202 Accepted와 requestId(PENDING 상태)를 반환한다.")
        @Test
        void returnsAccepted_withRequestId_whenCouponIsValid() {
            // arrange
            Coupon coupon = couponJpaRepository.save(
                new Coupon("선착순 100명", CouponType.FIXED, 1000, 0, ZonedDateTime.now().plusYears(1), 100)
            );

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ISSUE_ASYNC_ENDPOINT + "/" + coupon.getId() + "/issue-async",
                HttpMethod.POST,
                new HttpEntity<>(authHeaders(LOGIN_ID, PASSWORD)),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED),
                () -> assertThat(response.getBody().data().get("requestId")).isNotNull(),
                () -> assertThat(response.getBody().data().get("status")).isEqualTo("PENDING")
            );
        }

        @DisplayName("존재하지 않는 couponId로 요청하면, 404를 반환한다.")
        @Test
        void returnsNotFound_whenCouponDoesNotExist() {
            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ISSUE_ASYNC_ENDPOINT + "/99999/issue-async",
                HttpMethod.POST,
                new HttpEntity<>(authHeaders(LOGIN_ID, PASSWORD)),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("인증 실패 시, 401을 반환한다.")
        @Test
        void returnsUnauthorized_whenAuthFails() {
            // arrange
            Coupon coupon = couponJpaRepository.save(
                new Coupon("선착순 쿠폰", CouponType.FIXED, 1000, 0, ZonedDateTime.now().plusYears(1))
            );

            // act
            HttpHeaders wrongHeaders = new HttpHeaders();
            wrongHeaders.set("X-Loopers-LoginId", LOGIN_ID);
            wrongHeaders.set("X-Loopers-LoginPw", "WrongPass1!");
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ISSUE_ASYNC_ENDPOINT + "/" + coupon.getId() + "/issue-async",
                HttpMethod.POST,
                new HttpEntity<>(wrongHeaders),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("GET /api/v1/coupons/issue-requests/{requestId} (발급 상태 폴링)")
    @Nested
    class GetIssueRequestStatus {

        @DisplayName("본인의 발급 요청 상태를 조회하면, 200 OK와 상태 정보를 반환한다.")
        @Test
        void returnsStatus_whenOwnRequest() {
            // arrange
            Coupon coupon = couponJpaRepository.save(
                new Coupon("선착순 100명", CouponType.FIXED, 1000, 0, ZonedDateTime.now().plusYears(1), 100)
            );
            ResponseEntity<ApiResponse<Map<String, Object>>> issueResponse = testRestTemplate.exchange(
                ISSUE_ASYNC_ENDPOINT + "/" + coupon.getId() + "/issue-async",
                HttpMethod.POST,
                new HttpEntity<>(authHeaders(LOGIN_ID, PASSWORD)),
                new ParameterizedTypeReference<>() {}
            );
            String requestId = (String) issueResponse.getBody().data().get("requestId");

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ISSUE_REQUESTS_ENDPOINT + "/" + requestId,
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(LOGIN_ID, PASSWORD)),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().get("requestId")).isEqualTo(requestId),
                () -> assertThat(response.getBody().data().get("status")).isEqualTo("PENDING")
            );
        }

        @DisplayName("다른 유저의 발급 요청 상태를 조회하면, 403 Forbidden을 반환한다.")
        @Test
        void returnsForbidden_whenRequestBelongsToOtherUser() {
            // arrange
            Coupon coupon = couponJpaRepository.save(
                new Coupon("선착순 100명", CouponType.FIXED, 1000, 0, ZonedDateTime.now().plusYears(1), 100)
            );
            ResponseEntity<ApiResponse<Map<String, Object>>> issueResponse = testRestTemplate.exchange(
                ISSUE_ASYNC_ENDPOINT + "/" + coupon.getId() + "/issue-async",
                HttpMethod.POST,
                new HttpEntity<>(authHeaders(LOGIN_ID, PASSWORD)),
                new ParameterizedTypeReference<>() {}
            );
            String requestId = (String) issueResponse.getBody().data().get("requestId");

            // act - 다른 유저가 조회 시도
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ISSUE_REQUESTS_ENDPOINT + "/" + requestId,
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(OTHER_LOGIN_ID, OTHER_PASSWORD)),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @DisplayName("존재하지 않는 requestId로 조회하면, 404를 반환한다.")
        @Test
        void returnsNotFound_whenRequestDoesNotExist() {
            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ISSUE_REQUESTS_ENDPOINT + "/non-existent-request-id",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(LOGIN_ID, PASSWORD)),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}