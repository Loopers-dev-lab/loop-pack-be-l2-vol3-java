package com.loopers.interfaces.api;

import com.loopers.domain.coupon.CouponIssueResultStatus;
import com.loopers.domain.coupon.CouponType;
import com.loopers.interfaces.api.coupon.CouponV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Coupon Customer API E2E 테스트")
class CouponV1ApiE2ETest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private Long createCoupon(String name) {
        CouponV1Dto.CreateRequest request = new CouponV1Dto.CreateRequest(
                name, CouponType.FIXED, new BigDecimal("5000"),
                null, ZonedDateTime.now().plusDays(30), null
        );
        ResponseEntity<ApiResponse<CouponV1Dto.Response>> response = restTemplate.exchange(
                "/api-admin/v1/coupons",
                HttpMethod.POST,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<>() {}
        );
        return response.getBody().data().id();
    }

    @Nested
    @DisplayName("POST /api/v1/coupons/{couponId}/issue - 쿠폰 발급 요청")
    class IssueCoupon {

        @Test
        @DisplayName("성공: 쿠폰 발급 요청이 접수되고 PROCESSING 상태를 반환한다")
        void issueCoupon_Success() {
            // Given
            Long couponId = createCoupon("테스트쿠폰");
            Long userId = 1L;

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-User-Id", userId.toString());

            // When
            ResponseEntity<ApiResponse<CouponV1Dto.CouponIssueResultResponse>> response = restTemplate.exchange(
                    "/api/v1/coupons/" + couponId + "/issue",
                    HttpMethod.POST,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {}
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
            CouponV1Dto.CouponIssueResultResponse data = response.getBody().data();
            assertThat(data.userId()).isEqualTo(userId);
            assertThat(data.couponId()).isEqualTo(couponId);
            assertThat(data.status()).isEqualTo(CouponIssueResultStatus.PROCESSING);
        }

        @Test
        @DisplayName("실패: 중복 요청 시 UNIQUE 제약으로 실패한다")
        void issueCoupon_Duplicate() {
            // Given
            Long couponId = createCoupon("테스트쿠폰");
            Long userId = 1L;

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-User-Id", userId.toString());

            // 첫 번째 발급 요청
            restTemplate.exchange(
                    "/api/v1/coupons/" + couponId + "/issue",
                    HttpMethod.POST,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<ApiResponse<CouponV1Dto.CouponIssueResultResponse>>() {}
            );

            // When - 두 번째 발급 요청
            ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                    "/api/v1/coupons/" + couponId + "/issue",
                    HttpMethod.POST,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {}
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Nested
    @DisplayName("GET /api/v1/coupons/{couponId}/issue-result - 발급 결과 조회")
    class GetIssueResult {

        @Test
        @DisplayName("성공: 발급 요청 후 결과를 조회한다")
        void getIssueResult_Success() {
            // Given
            Long couponId = createCoupon("테스트쿠폰");
            Long userId = 1L;

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-User-Id", userId.toString());

            restTemplate.exchange(
                    "/api/v1/coupons/" + couponId + "/issue",
                    HttpMethod.POST,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<ApiResponse<CouponV1Dto.CouponIssueResultResponse>>() {}
            );

            // When
            ResponseEntity<ApiResponse<CouponV1Dto.CouponIssueResultResponse>> response = restTemplate.exchange(
                    "/api/v1/coupons/" + couponId + "/issue-result",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {}
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            CouponV1Dto.CouponIssueResultResponse data = response.getBody().data();
            assertThat(data.userId()).isEqualTo(userId);
            assertThat(data.couponId()).isEqualTo(couponId);
        }
    }

    @Nested
    @DisplayName("GET /api/v1/users/me/coupons - 내 쿠폰 목록")
    class GetMyCoupons {

        @Test
        @DisplayName("성공: 내 쿠폰 목록을 조회한다")
        void getMyCoupons_Success() {
            // Given
            Long userId = 1L;

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-User-Id", userId.toString());

            // When - 쿠폰이 없는 상태에서 목록 조회
            ResponseEntity<ApiResponse<List<CouponV1Dto.UserCouponResponse>>> response = restTemplate.exchange(
                    "/api/v1/users/me/coupons",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {}
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data()).isEmpty();
        }
    }
}
