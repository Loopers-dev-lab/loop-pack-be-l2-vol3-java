package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponFacade;
import com.loopers.application.coupon.CouponTemplateInfo;
import com.loopers.domain.coupon.CouponService;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.user.UserV1Dto;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
 * 대고객 쿠폰 API E2E: 발급, 내 쿠폰 목록.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(MySqlTestContainersConfig.class)
class CouponV1ApiE2ETest {

    private static final String ENDPOINT_ISSUE = "/api/v1/coupons";
    private static final String ENDPOINT_ISSUE_REQUESTS = "/api/v1/coupons/issue-requests";
    private static final String ENDPOINT_MY_COUPONS = "/api/v1/users/me/coupons";
    private static final String LOGIN_ID = "couponuser";

    @Autowired
    private TestRestTemplate testRestTemplate;
    @Autowired
    private DatabaseCleanUp databaseCleanUp;
    @Autowired
    private CouponFacade couponFacade;
    @Autowired
    private CouponService couponService;

    private Long templateId;

    @BeforeEach
    void setUp() {
        UserV1Dto.SignUpRequest signUp = new UserV1Dto.SignUpRequest(
                LOGIN_ID, "SecurePass1!", "coupon@example.com", "1990-01-15", "MALE");
        testRestTemplate.exchange("/api/v1/users", HttpMethod.POST, new HttpEntity<>(signUp),
                new ParameterizedTypeReference<ApiResponse<UserV1Dto.SignUpResponse>>() {
                });

        CouponTemplateInfo info = couponFacade.registerTemplate(
                "E2E 정액 쿠폰", "FIXED", 1000,
                BigDecimal.valueOf(5000), ZonedDateTime.now().plusDays(30), null);
        templateId = info.id();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders userHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Loopers-LoginId", LOGIN_ID);
        return h;
    }

    @DisplayName("POST /api/v1/coupons/{couponId}/issue - 쿠폰 발급")
    @Nested
    class IssueCoupon {

        @Test
        void issueCoupon_withValidRequest_shouldReturn201() {
            ResponseEntity<ApiResponse<CouponV1Dto.IssuedCouponResponse>> response = testRestTemplate.exchange(
                    ENDPOINT_ISSUE + "/" + templateId + "/issue", HttpMethod.POST, new HttpEntity<>(userHeaders()),
                    new ParameterizedTypeReference<>() {
                    });

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(Result.SUCCESS),
                    () -> assertThat(response.getBody().data().id()).isNotNull(),
                    () -> assertThat(response.getBody().data().couponId()).isEqualTo(templateId),
                    () -> assertThat(response.getBody().data().status()).isEqualTo("AVAILABLE"));
        }

        @Test
        void issueCoupon_withoutLogin_shouldReturn401() {
            ResponseEntity<ApiResponse<CouponV1Dto.IssuedCouponResponse>> response = testRestTemplate.exchange(
                    ENDPOINT_ISSUE + "/" + templateId + "/issue", HttpMethod.POST, new HttpEntity<>(null),
                    new ParameterizedTypeReference<>() {
                    });

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        void issueCoupon_withNonExistentTemplate_shouldReturn404() {
            ResponseEntity<ApiResponse<CouponV1Dto.IssuedCouponResponse>> response = testRestTemplate.exchange(
                    ENDPOINT_ISSUE + "/999999/issue", HttpMethod.POST, new HttpEntity<>(userHeaders()),
                    new ParameterizedTypeReference<>() {
                    });

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        void requestIssueCouponAsync_withValidRequest_shouldReturn202() {
            ResponseEntity<ApiResponse<CouponV1Dto.CouponIssueRequestResponse>> response = testRestTemplate.exchange(
                    ENDPOINT_ISSUE + "/" + templateId + "/issue-requests", HttpMethod.POST,
                    new HttpEntity<>(userHeaders()),
                    new ParameterizedTypeReference<>() {
                    });

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(Result.SUCCESS),
                    () -> assertThat(response.getBody().data().requestId()).isNotBlank(),
                    () -> assertThat(response.getBody().data().couponId()).isEqualTo(templateId),
                    () -> assertThat(response.getBody().data().status()).isEqualTo("PENDING"));
        }

        @Test
        void requestIssueCouponAsync_whenAlreadyIssued_thenStatusBecomesRejectedDuplicate() {
            // 이미 같은 쿠폰을 보유한 상태를 만든다.
            testRestTemplate.exchange(
                    ENDPOINT_ISSUE + "/" + templateId + "/issue", HttpMethod.POST, new HttpEntity<>(userHeaders()),
                    new ParameterizedTypeReference<ApiResponse<CouponV1Dto.IssuedCouponResponse>>() {
                    });

            ResponseEntity<ApiResponse<CouponV1Dto.CouponIssueRequestResponse>> requestResponse = testRestTemplate
                    .exchange(
                            ENDPOINT_ISSUE + "/" + templateId + "/issue-requests", HttpMethod.POST,
                            new HttpEntity<>(userHeaders()),
                            new ParameterizedTypeReference<>() {
                            });

            String requestId = requestResponse.getBody().data().requestId();
            Long userId = requestResponse.getBody().data().userId();
            couponService.processCouponIssueRequest(requestId, userId, templateId);

            ResponseEntity<ApiResponse<CouponV1Dto.CouponIssueRequestResponse>> getResponse = testRestTemplate.exchange(
                    ENDPOINT_ISSUE_REQUESTS + "/" + requestId, HttpMethod.GET, new HttpEntity<>(userHeaders()),
                    new ParameterizedTypeReference<>() {
                    });

            assertAll(
                    () -> assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(getResponse.getBody().meta().result()).isEqualTo(Result.SUCCESS),
                    () -> assertThat(getResponse.getBody().data().requestId()).isEqualTo(requestId),
                    () -> assertThat(getResponse.getBody().data().status()).isEqualTo("REJECTED_DUPLICATE"));
        }
    }

    @DisplayName("GET /api/v1/users/me/coupons - 내 쿠폰 목록")
    @Nested
    class GetMyCoupons {

        @Test
        void getMyCoupons_afterIssue_shouldReturnListWithIssuedCoupon() {
            testRestTemplate.exchange(
                    ENDPOINT_ISSUE + "/" + templateId + "/issue", HttpMethod.POST, new HttpEntity<>(userHeaders()),
                    new ParameterizedTypeReference<ApiResponse<CouponV1Dto.IssuedCouponResponse>>() {
                    });

            ResponseEntity<ApiResponse<CouponV1Dto.PagedIssuedCouponsResponse>> response = testRestTemplate.exchange(
                    ENDPOINT_MY_COUPONS + "?page=0&size=20", HttpMethod.GET, new HttpEntity<>(userHeaders()),
                    new ParameterizedTypeReference<>() {
                    });

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(Result.SUCCESS),
                    () -> assertThat(response.getBody().data().content()).hasSize(1),
                    () -> assertThat(response.getBody().data().content().get(0).couponId()).isEqualTo(templateId),
                    () -> assertThat(response.getBody().data().content().get(0).status()).isEqualTo("AVAILABLE"));
        }

        @Test
        void getMyCoupons_withoutLogin_shouldReturn401() {
            ResponseEntity<ApiResponse<CouponV1Dto.PagedIssuedCouponsResponse>> response = testRestTemplate.exchange(
                    ENDPOINT_MY_COUPONS, HttpMethod.GET, new HttpEntity<>(null),
                    new ParameterizedTypeReference<>() {
                    });

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        void getMyCoupons_whenNoCoupons_shouldReturnEmptyPage() {
            ResponseEntity<ApiResponse<CouponV1Dto.PagedIssuedCouponsResponse>> response = testRestTemplate.exchange(
                    ENDPOINT_MY_COUPONS, HttpMethod.GET, new HttpEntity<>(userHeaders()),
                    new ParameterizedTypeReference<>() {
                    });

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().content()).isEmpty();
        }
    }
}
