package com.loopers.interfaces.api.coupon;

import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponTemplateRepository;
import com.loopers.domain.coupon.DiscountType;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.user.UserRequest;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CouponApiE2ETest {

    @Autowired private TestRestTemplate testRestTemplate;
    @Autowired private CouponTemplateRepository couponTemplateRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @BeforeEach
    void setUp() {
        UserRequest.SignupRequest signupRequest = new UserRequest.SignupRequest(
                "testuser", "Hx7!mK2@", "테스터", "1994-11-15", "test@example.com");
        testRestTemplate.postForEntity("/api/v1/users", signupRequest, ApiResponse.class);
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", "testuser");
        headers.set("X-Loopers-LoginPw", "Hx7!mK2@");
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private CouponTemplate createActiveTemplate() {
        return couponTemplateRepository.save(CouponTemplate.create(
                "신규 가입 쿠폰", "5000원 할인", DiscountType.FIXED, 5000, null,
                10000, 100, 1,
                ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(30)));
    }

    @DisplayName("POST /api/v1/coupons/issue")
    @Nested
    class 쿠폰_발급 {

        @Test
        void 발급에_성공하면_200_OK를_반환한다() {
            // arrange
            CouponTemplate template = createActiveTemplate();
            CouponRequest.IssueCouponRequest request =
                    new CouponRequest.IssueCouponRequest(template.getId());

            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    "/api/v1/coupons/issue", HttpMethod.POST,
                    new HttpEntity<>(request, authHeaders()), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void 존재하지_않는_템플릿이면_404_Not_Found를_반환한다() {
            // act
            CouponRequest.IssueCouponRequest request = new CouponRequest.IssueCouponRequest(999L);
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    "/api/v1/coupons/issue", HttpMethod.POST,
                    new HttpEntity<>(request, authHeaders()), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        void 유저별_발급_제한_초과면_409_Conflict를_반환한다() {
            // arrange
            CouponTemplate template = createActiveTemplate();
            CouponRequest.IssueCouponRequest request =
                    new CouponRequest.IssueCouponRequest(template.getId());
            // 첫 발급
            testRestTemplate.exchange("/api/v1/coupons/issue", HttpMethod.POST,
                    new HttpEntity<>(request, authHeaders()), ApiResponse.class);

            // act - 재발급 시도 (maxIssueCountPerUser=1)
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    "/api/v1/coupons/issue", HttpMethod.POST,
                    new HttpEntity<>(request, authHeaders()), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        void 인증_없이_요청하면_401_Unauthorized를_반환한다() {
            // act
            CouponRequest.IssueCouponRequest request = new CouponRequest.IssueCouponRequest(1L);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    "/api/v1/coupons/issue", HttpMethod.POST,
                    new HttpEntity<>(request, headers), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("GET /api/v1/users/me/coupons")
    @Nested
    class 내_쿠폰_목록_조회 {

        @Test
        void 조회에_성공하면_200_OK를_반환한다() {
            // arrange
            CouponTemplate template = createActiveTemplate();
            CouponRequest.IssueCouponRequest request =
                    new CouponRequest.IssueCouponRequest(template.getId());
            testRestTemplate.exchange("/api/v1/coupons/issue", HttpMethod.POST,
                    new HttpEntity<>(request, authHeaders()), ApiResponse.class);

            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    "/api/v1/users/me/coupons", HttpMethod.GET,
                    new HttpEntity<>(authHeaders()), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void 쿠폰이_없으면_빈_목록을_반환한다() {
            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    "/api/v1/users/me/coupons", HttpMethod.GET,
                    new HttpEntity<>(authHeaders()), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }
}
