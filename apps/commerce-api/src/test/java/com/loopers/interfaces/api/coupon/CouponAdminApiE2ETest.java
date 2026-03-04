package com.loopers.interfaces.api.coupon;

import com.loopers.domain.coupon.CouponType;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.E2ETestFixture;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
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
class CouponAdminApiE2ETest {

    private static final String ENDPOINT = "/api-admin/v1/coupons";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private E2ETestFixture fixture;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    class 쿠폰_등록 {

        @Test
        void 유효한_정보로_등록하면_200_응답과_쿠폰_정보를_반환한다() {
            CouponRequest.Register request = new CouponRequest.Register(
                    "1000원 할인", CouponType.FIXED, 1000,
                    BigDecimal.valueOf(10000), 100, LocalDateTime.now().plusDays(7)
            );

            ResponseEntity<ApiResponse<CouponAdminV1Dto.CouponResponse>> response = postRegister(request);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().id()).isNotNull(),
                    () -> assertThat(response.getBody().data().name()).isEqualTo("1000원 할인"),
                    () -> assertThat(response.getBody().data().type()).isEqualTo("FIXED"),
                    () -> assertThat(response.getBody().data().value()).isEqualTo(1000),
                    () -> assertThat(response.getBody().data().minOrderAmount()).isEqualByComparingTo(BigDecimal.valueOf(10000)),
                    () -> assertThat(response.getBody().data().maxIssueCount()).isEqualTo(100),
                    () -> assertThat(response.getBody().data().issuedCount()).isEqualTo(0),
                    () -> assertThat(response.getBody().data().expiredAt()).isNotNull(),
                    () -> assertThat(response.getBody().data().createdAt()).isNotNull(),
                    () -> assertThat(response.getBody().data().updatedAt()).isNotNull()
            );
        }

        @Test
        void 정률_타입으로_등록하면_200_응답() {
            CouponRequest.Register request = new CouponRequest.Register(
                    "10% 할인", CouponType.RATE, 10,
                    null, 50, LocalDateTime.now().plusDays(7)
            );

            ResponseEntity<ApiResponse<CouponAdminV1Dto.CouponResponse>> response = postRegister(request);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().type()).isEqualTo("RATE"),
                    () -> assertThat(response.getBody().data().minOrderAmount()).isNull()
            );
        }

        @Test
        void 정률_타입_할인값이_100을_초과하면_400_응답() {
            CouponRequest.Register request = new CouponRequest.Register(
                    "101% 할인", CouponType.RATE, 101,
                    null, 100, LocalDateTime.now().plusDays(7)
            );

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT, HttpMethod.POST,
                    new HttpEntity<>(request, fixture.adminHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                    () -> assertThat(response.getBody().meta().message()).contains("유효하지 않은 할인값입니다")
            );
        }

        @Test
        void 만료일이_현재보다_과거이면_400_응답() {
            CouponRequest.Register request = new CouponRequest.Register(
                    "쿠폰", CouponType.FIXED, 1000,
                    null, 100, LocalDateTime.now().minusDays(1)
            );

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT, HttpMethod.POST,
                    new HttpEntity<>(request, fixture.adminHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                    () -> assertThat(response.getBody().meta().message()).contains("만료일은 현재 이후여야 합니다")
            );
        }

        @Test
        void 쿠폰명이_빈값이면_400_응답() {
            CouponRequest.Register request = new CouponRequest.Register(
                    "", CouponType.FIXED, 1000,
                    null, 100, LocalDateTime.now().plusDays(7)
            );

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT, HttpMethod.POST,
                    new HttpEntity<>(request, fixture.adminHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void 인증헤더가_누락되면_401_응답() {
            CouponRequest.Register request = new CouponRequest.Register(
                    "쿠폰", CouponType.FIXED, 1000,
                    null, 100, LocalDateTime.now().plusDays(7)
            );

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT, HttpMethod.POST,
                    new HttpEntity<>(request, new HttpHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED),
                    () -> assertThat(response.getBody().meta().message()).contains("인증 헤더가 필요합니다")
            );
        }

        @Test
        void 인증에_실패하면_401_응답() {
            CouponRequest.Register request = new CouponRequest.Register(
                    "쿠폰", CouponType.FIXED, 1000,
                    null, 100, LocalDateTime.now().plusDays(7)
            );

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-Ldap", "wrong-ldap");

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT, HttpMethod.POST,
                    new HttpEntity<>(request, headers),
                    new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED),
                    () -> assertThat(response.getBody().meta().message()).contains("인증에 실패했습니다")
            );
        }
    }

    private ResponseEntity<ApiResponse<CouponAdminV1Dto.CouponResponse>> postRegister(
            CouponRequest.Register request) {
        return testRestTemplate.exchange(
                ENDPOINT, HttpMethod.POST,
                new HttpEntity<>(request, fixture.adminHeaders()),
                new ParameterizedTypeReference<>() {}
        );
    }
}
