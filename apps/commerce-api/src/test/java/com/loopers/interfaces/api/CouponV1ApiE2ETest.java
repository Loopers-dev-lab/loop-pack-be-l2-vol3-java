package com.loopers.interfaces.api;

import com.loopers.domain.coupon.CouponStatus;
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
                null, ZonedDateTime.now().plusDays(30)
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
    @DisplayName("POST /api/v1/coupons/{couponId}/issue - 쿠폰 발급")
    class IssueCoupon {

        @Test
        @DisplayName("성공: 쿠폰을 발급받는다")
        void issueCoupon_Success() {
            // Given
            Long couponId = createCoupon("테스트쿠폰");
            Long userId = 1L;

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-User-Id", userId.toString());

            // When
            ResponseEntity<ApiResponse<CouponV1Dto.UserCouponResponse>> response = restTemplate.exchange(
                    "/api/v1/coupons/" + couponId + "/issue",
                    HttpMethod.POST,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {}
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            CouponV1Dto.UserCouponResponse data = response.getBody().data();
            assertThat(data.userId()).isEqualTo(userId);
            assertThat(data.couponId()).isEqualTo(couponId);
            assertThat(data.status()).isEqualTo(CouponStatus.AVAILABLE);
        }

        @Test
        @DisplayName("실패: 중복 발급 시 409 CONFLICT")
        void issueCoupon_Duplicate() {
            // Given
            Long couponId = createCoupon("테스트쿠폰");
            Long userId = 1L;

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-User-Id", userId.toString());

            // 첫 번째 발급
            restTemplate.exchange(
                    "/api/v1/coupons/" + couponId + "/issue",
                    HttpMethod.POST,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<ApiResponse<CouponV1Dto.UserCouponResponse>>() {}
            );

            // When - 두 번째 발급 시도
            ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                    "/api/v1/coupons/" + couponId + "/issue",
                    HttpMethod.POST,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {}
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }
    }

    @Nested
    @DisplayName("GET /api/v1/users/me/coupons - 내 쿠폰 목록")
    class GetMyCoupons {

        @Test
        @DisplayName("성공: 내 쿠폰 목록을 조회한다")
        void getMyCoupons_Success() {
            // Given
            Long couponId1 = createCoupon("쿠폰1");
            Long couponId2 = createCoupon("쿠폰2");
            Long userId = 1L;

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-User-Id", userId.toString());

            // 쿠폰 발급
            restTemplate.exchange("/api/v1/coupons/" + couponId1 + "/issue", HttpMethod.POST, new HttpEntity<>(headers), new ParameterizedTypeReference<ApiResponse<CouponV1Dto.UserCouponResponse>>() {});
            restTemplate.exchange("/api/v1/coupons/" + couponId2 + "/issue", HttpMethod.POST, new HttpEntity<>(headers), new ParameterizedTypeReference<ApiResponse<CouponV1Dto.UserCouponResponse>>() {});

            // When
            ResponseEntity<ApiResponse<List<CouponV1Dto.UserCouponResponse>>> response = restTemplate.exchange(
                    "/api/v1/users/me/coupons",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {}
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data()).hasSize(2);
        }
    }
}
