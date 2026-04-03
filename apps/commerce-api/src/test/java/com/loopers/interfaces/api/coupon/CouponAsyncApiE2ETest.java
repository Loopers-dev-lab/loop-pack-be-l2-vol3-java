package com.loopers.interfaces.api.coupon;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.E2ETestFixture;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.concurrent.CompletableFuture;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(E2ETestFixture.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CouponAsyncApiE2ETest {

    private static final String ISSUE_ASYNC_ENDPOINT = "/api/v1/coupons/{couponId}/issue-async";
    private static final String ISSUE_STATUS_ENDPOINT = "/api/v1/coupons/{couponId}/issue-status";
    private static final String LOGIN_ID = "testuser";
    private static final String LOGIN_PW = "Test1234!";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private E2ETestFixture fixture;

    @MockBean
    private KafkaTemplate<Object, Object> kafkaTemplate;

    private Long couponId;

    @BeforeEach
    void setUp() {
        databaseCleanUp.truncateAllTables();
        org.mockito.Mockito.when(kafkaTemplate.send(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        fixture.signUp(LOGIN_ID, LOGIN_PW, "테스트유저", "test@example.com");
        couponId = fixture.registerCoupon("테스트쿠폰", "FIXED", 1000,
                BigDecimal.ZERO, 100, LocalDateTime.now().plusDays(7));
    }

    @Nested
    class 비동기_발급_요청 {

        @Test
        void 유효한_요청이면_200_응답과_PENDING_상태가_반환된다() {
            ResponseEntity<ApiResponse<CouponV1Dto.CouponIssueRequestResponse>> response =
                    issueAsync(couponId);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().status()).isEqualTo("PENDING"),
                    () -> assertThat(response.getBody().data().requestId()).isNotNull()
            );
        }

        @Test
        void 이미_요청한_couponId_userId면_기존_상태가_반환된다() {
            issueAsync(couponId);

            ResponseEntity<ApiResponse<CouponV1Dto.CouponIssueRequestResponse>> response =
                    issueAsync(couponId);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().status()).isEqualTo("PENDING")
            );
        }

        @Test
        void 존재하지_않는_couponId면_404_응답이다() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ISSUE_ASYNC_ENDPOINT, HttpMethod.POST,
                    new HttpEntity<>(fixture.userHeaders(LOGIN_ID, LOGIN_PW)),
                    new ParameterizedTypeReference<>() {},
                    99999L);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        void 인증_헤더가_없으면_401_응답이다() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ISSUE_ASYNC_ENDPOINT, HttpMethod.POST,
                    new HttpEntity<>(new HttpHeaders()),
                    new ParameterizedTypeReference<>() {},
                    couponId);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Nested
    class 발급_상태_조회 {

        @Test
        void PENDING_상태의_요청을_조회하면_200_응답과_PENDING이_반환된다() {
            issueAsync(couponId);

            ResponseEntity<ApiResponse<CouponV1Dto.CouponIssueRequestResponse>> response =
                    testRestTemplate.exchange(
                            ISSUE_STATUS_ENDPOINT, HttpMethod.GET,
                            new HttpEntity<>(fixture.userHeaders(LOGIN_ID, LOGIN_PW)),
                            new ParameterizedTypeReference<>() {},
                            couponId);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().status()).isEqualTo("PENDING")
            );
        }

        @Test
        void 요청하지_않은_couponId를_조회하면_404_응답이다() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ISSUE_STATUS_ENDPOINT, HttpMethod.GET,
                    new HttpEntity<>(fixture.userHeaders(LOGIN_ID, LOGIN_PW)),
                    new ParameterizedTypeReference<>() {},
                    couponId);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    private ResponseEntity<ApiResponse<CouponV1Dto.CouponIssueRequestResponse>> issueAsync(Long couponId) {
        return testRestTemplate.exchange(
                ISSUE_ASYNC_ENDPOINT, HttpMethod.POST,
                new HttpEntity<>(fixture.userHeaders(LOGIN_ID, LOGIN_PW)),
                new ParameterizedTypeReference<>() {},
                couponId);
    }
}
