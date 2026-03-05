package com.loopers.interfaces.api;

import com.loopers.interfaces.api.coupon.AdminCouponV1Dto;
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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminCouponV1ApiE2ETest {

    private static final String ENDPOINT = "/api/v1/admin/coupons";

    private final TestRestTemplate testRestTemplate;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public AdminCouponV1ApiE2ETest(TestRestTemplate testRestTemplate, DatabaseCleanUp databaseCleanUp) {
        this.testRestTemplate = testRestTemplate;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("쿠폰 생성 시")
    @Nested
    class CreateCoupon {

        @DisplayName("유효한 요청이면, 201 Created와 쿠폰 정보를 반환한다.")
        @Test
        void returnsCreated_whenValidRequest() {
            // arrange
            AdminCouponV1Dto.CreateRequest request = new AdminCouponV1Dto.CreateRequest(
                "신규 회원 쿠폰",
                "FIXED",
                1000L,
                0L,
                LocalDateTime.now().plusDays(30));
            HttpEntity<AdminCouponV1Dto.CreateRequest> entity = new HttpEntity<>(request);

            // act
            ResponseEntity<ApiResponse<AdminCouponV1Dto.CouponResponse>> response =
                testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                () -> assertThat(response.getBody().data().id()).isNotNull(),
                () -> assertThat(response.getBody().data().name()).isEqualTo("신규 회원 쿠폰"),
                () -> assertThat(response.getBody().data().discountType()).isEqualTo("FIXED"),
                () -> assertThat(response.getBody().data().discountValue()).isEqualTo(1000L)
            );
        }

        @DisplayName("name이 비어있으면, 400 Bad Request를 반환한다.")
        @Test
        void returnsBadRequest_whenNameIsBlank() {
            // arrange
            AdminCouponV1Dto.CreateRequest request = new AdminCouponV1Dto.CreateRequest(
                "",
                "FIXED",
                1000L,
                0L,
                LocalDateTime.now().plusDays(30)
            );
            HttpEntity<AdminCouponV1Dto.CreateRequest> entity = new HttpEntity<>(request);

            // act
            ResponseEntity<ApiResponse<Void>> response =
                testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("discountValue가 0 이하이면, 400 Bad Request를 반환한다.")
        @Test
        void returnsBadRequest_whenDiscountValueIsZero() {
            // arrange
            AdminCouponV1Dto.CreateRequest request = new AdminCouponV1Dto.CreateRequest(
                "쿠폰",
                "FIXED",
                0L,
                0L,
                LocalDateTime.now().plusDays(30)
            );
            HttpEntity<AdminCouponV1Dto.CreateRequest> entity = new HttpEntity<>(request);

            // act
            ResponseEntity<ApiResponse<Void>> response =
                testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }
}
