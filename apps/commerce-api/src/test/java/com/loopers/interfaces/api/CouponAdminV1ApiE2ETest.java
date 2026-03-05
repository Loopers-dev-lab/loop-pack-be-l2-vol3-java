package com.loopers.interfaces.api;

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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Coupon Admin API E2E 테스트")
class CouponAdminV1ApiE2ETest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("POST /api-admin/v1/coupons - 쿠폰 생성")
    class CreateCoupon {

        @Test
        @DisplayName("성공: 정액 할인 쿠폰을 생성한다")
        void createCoupon_Success() {
            // Given
            CouponV1Dto.CreateRequest request = new CouponV1Dto.CreateRequest(
                    "신규회원 5000원 할인", CouponType.FIXED, new BigDecimal("5000"),
                    new BigDecimal("10000"), ZonedDateTime.now().plusDays(30)
            );

            // When
            ResponseEntity<ApiResponse<CouponV1Dto.Response>> response = restTemplate.exchange(
                    "/api-admin/v1/coupons",
                    HttpMethod.POST,
                    new HttpEntity<>(request),
                    new ParameterizedTypeReference<>() {}
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            CouponV1Dto.Response data = response.getBody().data();
            assertThat(data.id()).isNotNull();
            assertThat(data.name()).isEqualTo("신규회원 5000원 할인");
            assertThat(data.type()).isEqualTo(CouponType.FIXED);
            assertThat(data.value()).isEqualByComparingTo(new BigDecimal("5000"));
        }
    }

    @Nested
    @DisplayName("GET /api-admin/v1/coupons/{couponId} - 쿠폰 조회")
    class GetCoupon {

        @Test
        @DisplayName("성공: 쿠폰을 조회한다")
        void getCoupon_Success() {
            // Given
            CouponV1Dto.CreateRequest request = new CouponV1Dto.CreateRequest(
                    "테스트쿠폰", CouponType.RATE, new BigDecimal("10"),
                    null, ZonedDateTime.now().plusDays(30)
            );

            ResponseEntity<ApiResponse<CouponV1Dto.Response>> createResponse = restTemplate.exchange(
                    "/api-admin/v1/coupons",
                    HttpMethod.POST,
                    new HttpEntity<>(request),
                    new ParameterizedTypeReference<>() {}
            );
            Long couponId = createResponse.getBody().data().id();

            // When
            ResponseEntity<ApiResponse<CouponV1Dto.Response>> response = restTemplate.exchange(
                    "/api-admin/v1/coupons/" + couponId,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().name()).isEqualTo("테스트쿠폰");
        }

        @Test
        @DisplayName("실패: 존재하지 않는 쿠폰 조회 시 404")
        void getCoupon_NotFound() {
            // When
            ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                    "/api-admin/v1/coupons/999",
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("GET /api-admin/v1/coupons - 쿠폰 목록 조회")
    class GetCoupons {

        @Test
        @DisplayName("성공: 쿠폰 목록을 조회한다")
        void getCoupons_Success() {
            // Given
            restTemplate.exchange(
                    "/api-admin/v1/coupons",
                    HttpMethod.POST,
                    new HttpEntity<>(new CouponV1Dto.CreateRequest("쿠폰1", CouponType.FIXED, new BigDecimal("1000"), null, ZonedDateTime.now().plusDays(30))),
                    new ParameterizedTypeReference<ApiResponse<CouponV1Dto.Response>>() {}
            );
            restTemplate.exchange(
                    "/api-admin/v1/coupons",
                    HttpMethod.POST,
                    new HttpEntity<>(new CouponV1Dto.CreateRequest("쿠폰2", CouponType.RATE, new BigDecimal("10"), null, ZonedDateTime.now().plusDays(30))),
                    new ParameterizedTypeReference<ApiResponse<CouponV1Dto.Response>>() {}
            );

            // When
            ResponseEntity<ApiResponse<CouponV1Dto.PageResponse>> response = restTemplate.exchange(
                    "/api-admin/v1/coupons?page=0&size=10",
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().totalElements()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("PUT /api-admin/v1/coupons/{couponId} - 쿠폰 수정")
    class UpdateCoupon {

        @Test
        @DisplayName("성공: 쿠폰을 수정한다")
        void updateCoupon_Success() {
            // Given
            ResponseEntity<ApiResponse<CouponV1Dto.Response>> createResponse = restTemplate.exchange(
                    "/api-admin/v1/coupons",
                    HttpMethod.POST,
                    new HttpEntity<>(new CouponV1Dto.CreateRequest("원래이름", CouponType.FIXED, new BigDecimal("1000"), null, ZonedDateTime.now().plusDays(30))),
                    new ParameterizedTypeReference<>() {}
            );
            Long couponId = createResponse.getBody().data().id();

            CouponV1Dto.UpdateRequest updateRequest = new CouponV1Dto.UpdateRequest(
                    "수정된이름", null, new BigDecimal("2000"), null, null
            );

            // When
            ResponseEntity<ApiResponse<CouponV1Dto.Response>> response = restTemplate.exchange(
                    "/api-admin/v1/coupons/" + couponId,
                    HttpMethod.PUT,
                    new HttpEntity<>(updateRequest),
                    new ParameterizedTypeReference<>() {}
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().name()).isEqualTo("수정된이름");
            assertThat(response.getBody().data().value()).isEqualByComparingTo(new BigDecimal("2000"));
        }
    }

    @Nested
    @DisplayName("DELETE /api-admin/v1/coupons/{couponId} - 쿠폰 삭제")
    class DeleteCoupon {

        @Test
        @DisplayName("성공: 쿠폰을 삭제한다")
        void deleteCoupon_Success() {
            // Given
            ResponseEntity<ApiResponse<CouponV1Dto.Response>> createResponse = restTemplate.exchange(
                    "/api-admin/v1/coupons",
                    HttpMethod.POST,
                    new HttpEntity<>(new CouponV1Dto.CreateRequest("삭제할쿠폰", CouponType.FIXED, new BigDecimal("1000"), null, ZonedDateTime.now().plusDays(30))),
                    new ParameterizedTypeReference<>() {}
            );
            Long couponId = createResponse.getBody().data().id();

            // When
            ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                    "/api-admin/v1/coupons/" + couponId,
                    HttpMethod.DELETE,
                    null,
                    new ParameterizedTypeReference<>() {}
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            // 삭제 후 조회 시 404
            ResponseEntity<ApiResponse<Void>> getResponse = restTemplate.exchange(
                    "/api-admin/v1/coupons/" + couponId,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );
            assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
