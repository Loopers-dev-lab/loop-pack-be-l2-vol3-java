package com.loopers.interfaces.api.admin;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.auth.AdminAuthInterceptor;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

import static com.loopers.interfaces.api.ApiResponse.Metadata.Result;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * 어드민 쿠폰 API E2E: 템플릿 CRUD, 발급 이력 조회.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(MySqlTestContainersConfig.class)
class AdminCouponV1ApiE2ETest {

    private static final String ENDPOINT = "/api-admin/v1/coupons";
    private static final String LDAP_ID = "admin-coupon-e2e";

    @Autowired
    private TestRestTemplate testRestTemplate;
    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Value("${loopers.admin.auth.signing-secret:change-me-in-production}")
    private String signingSecret;

    private Long createdCouponId;

    @BeforeEach
    void setUp() {
        AdminCouponV1Dto.CreateCouponRequest body = new AdminCouponV1Dto.CreateCouponRequest(
                "E2E어드민쿠폰", "FIXED", 2000, BigDecimal.valueOf(10000),
                ZonedDateTime.now().plusDays(7));
        ResponseEntity<ApiResponse<AdminCouponV1Dto.CouponResponse>> res = testRestTemplate.exchange(
                ENDPOINT, HttpMethod.POST, new HttpEntity<>(body, adminHeaders()),
                new ParameterizedTypeReference<>() {});
        assertThat(res.getBody()).isNotNull();
        createdCouponId = res.getBody().data().id();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders adminHeaders() {
        String signature = AdminAuthInterceptor.sign(LDAP_ID, signingSecret);
        HttpHeaders h = new HttpHeaders();
        h.set(AdminAuthInterceptor.HEADER_LDAP, LDAP_ID);
        h.set(AdminAuthInterceptor.HEADER_ADMIN_SIGNATURE, signature);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    @DisplayName("GET /api-admin/v1/coupons - 템플릿 목록")
    @Nested
    class GetCoupons {

        @Test
        void getCoupons_withValidAuth_shouldReturn200() {
            ResponseEntity<ApiResponse<AdminCouponV1Dto.PagedCouponsResponse>> response = testRestTemplate.exchange(
                    ENDPOINT + "?page=0&size=20", HttpMethod.GET, new HttpEntity<>(adminHeaders()),
                    new ParameterizedTypeReference<>() {});

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(Result.SUCCESS),
                    () -> assertThat(response.getBody().data().content()).isNotEmpty(),
                    () -> assertThat(response.getBody().data().content().get(0).name()).isEqualTo("E2E어드민쿠폰")
            );
        }
    }

    @DisplayName("GET /api-admin/v1/coupons/{couponId} - 템플릿 단건")
    @Nested
    class GetCoupon {

        @Test
        void getCoupon_withValidId_shouldReturn200() {
            ResponseEntity<ApiResponse<AdminCouponV1Dto.CouponResponse>> response = testRestTemplate.exchange(
                    ENDPOINT + "/" + createdCouponId, HttpMethod.GET, new HttpEntity<>(adminHeaders()),
                    new ParameterizedTypeReference<>() {});

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().id()).isEqualTo(createdCouponId),
                    () -> assertThat(response.getBody().data().type()).isEqualTo("FIXED"),
                    () -> assertThat(response.getBody().data().value()).isEqualTo(2000)
            );
        }

        @Test
        void getCoupon_withNonExistentId_shouldReturn404() {
            ResponseEntity<ApiResponse<AdminCouponV1Dto.CouponResponse>> response = testRestTemplate.exchange(
                    ENDPOINT + "/999999", HttpMethod.GET, new HttpEntity<>(adminHeaders()),
                    new ParameterizedTypeReference<>() {});

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("POST /api-admin/v1/coupons - 템플릿 생성")
    @Nested
    class CreateCoupon {

        @Test
        void createCoupon_withValidRequest_shouldReturn201() {
            AdminCouponV1Dto.CreateCouponRequest body = new AdminCouponV1Dto.CreateCouponRequest(
                    "새정률쿠폰", "RATE", 10, null, ZonedDateTime.now().plusDays(30));

            ResponseEntity<ApiResponse<AdminCouponV1Dto.CouponResponse>> response = testRestTemplate.exchange(
                    ENDPOINT, HttpMethod.POST, new HttpEntity<>(body, adminHeaders()),
                    new ParameterizedTypeReference<>() {});

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                    () -> assertThat(response.getBody().data().name()).isEqualTo("새정률쿠폰"),
                    () -> assertThat(response.getBody().data().type()).isEqualTo("RATE"),
                    () -> assertThat(response.getBody().data().value()).isEqualTo(10)
            );
        }
    }

    @DisplayName("PUT /api-admin/v1/coupons/{couponId} - 템플릿 수정")
    @Nested
    class UpdateCoupon {

        @Test
        void updateCoupon_withValidRequest_shouldReturn200() {
            AdminCouponV1Dto.UpdateCouponRequest body = new AdminCouponV1Dto.UpdateCouponRequest(
                    "수정된이름", "FIXED", 3000, BigDecimal.valueOf(15000), ZonedDateTime.now().plusDays(14));

            ResponseEntity<ApiResponse<AdminCouponV1Dto.CouponResponse>> response = testRestTemplate.exchange(
                    ENDPOINT + "/" + createdCouponId, HttpMethod.PUT, new HttpEntity<>(body, adminHeaders()),
                    new ParameterizedTypeReference<>() {});

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().name()).isEqualTo("수정된이름"),
                    () -> assertThat(response.getBody().data().value()).isEqualTo(3000)
            );
        }
    }

    @DisplayName("DELETE /api-admin/v1/coupons/{couponId} - 템플릿 삭제")
    @Nested
    class DeleteCoupon {

        @Test
        void deleteCoupon_withValidId_shouldReturn204() {
            ResponseEntity<Void> response = testRestTemplate.exchange(
                    ENDPOINT + "/" + createdCouponId, HttpMethod.DELETE, new HttpEntity<>(adminHeaders()),
                    Void.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        }
    }

    @DisplayName("GET /api-admin/v1/coupons/{couponId}/issues - 발급 이력")
    @Nested
    class GetCouponIssues {

        @Test
        void getCouponIssues_shouldReturn200() {
            ResponseEntity<ApiResponse<AdminCouponV1Dto.PagedIssuedCouponsResponse>> response = testRestTemplate.exchange(
                    ENDPOINT + "/" + createdCouponId + "/issues?page=0&size=20", HttpMethod.GET,
                    new HttpEntity<>(adminHeaders()), new ParameterizedTypeReference<>() {});

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().meta().result()).isEqualTo(Result.SUCCESS);
        }
    }
}
