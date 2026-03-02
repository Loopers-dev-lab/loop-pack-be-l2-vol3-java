package com.loopers.interfaces.api.coupon;

import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponTemplateRepository;
import com.loopers.domain.coupon.DiscountType;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
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
class AdminCouponTemplateApiE2ETest {

    @Autowired private TestRestTemplate testRestTemplate;
    @Autowired private CouponTemplateRepository couponTemplateRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-Ldap", "loopers.admin");
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private CouponTemplate createTemplate() {
        return couponTemplateRepository.save(CouponTemplate.create(
                "테스트 쿠폰", "테스트 설명", DiscountType.FIXED, 5000, null,
                10000, 100, 1,
                ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(30)));
    }

    @DisplayName("GET /api-admin/v1/coupon-templates")
    @Nested
    class 템플릿_목록_조회 {

        @Test
        void 조회에_성공하면_200_OK를_반환한다() {
            // arrange
            createTemplate();

            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    "/api-admin/v1/coupon-templates", HttpMethod.GET,
                    new HttpEntity<>(adminHeaders()), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void 인증_없이_요청하면_401_Unauthorized를_반환한다() {
            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    "/api-admin/v1/coupon-templates", HttpMethod.GET,
                    new HttpEntity<>(new HttpHeaders()), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("POST /api-admin/v1/coupon-templates")
    @Nested
    class 템플릿_생성 {

        @Test
        void 생성에_성공하면_200_OK를_반환한다() {
            // arrange
            AdminCouponTemplateRequest.CreateTemplateRequest request =
                    new AdminCouponTemplateRequest.CreateTemplateRequest(
                            "신규 쿠폰", "설명", "FIXED", 3000, null,
                            10000, 50, 2,
                            ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(30));

            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    "/api-admin/v1/coupon-templates", HttpMethod.POST,
                    new HttpEntity<>(request, adminHeaders()), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    @DisplayName("PATCH /api-admin/v1/coupon-templates/{templateId}")
    @Nested
    class 템플릿_수정 {

        @Test
        void 수정에_성공하면_200_OK를_반환한다() {
            // arrange
            CouponTemplate template = createTemplate();
            AdminCouponTemplateRequest.UpdateTemplateRequest request =
                    new AdminCouponTemplateRequest.UpdateTemplateRequest(
                            "수정된 쿠폰", "수정 설명", "PERCENT", 10, 5000, 20000);

            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    "/api-admin/v1/coupon-templates/" + template.getId(), HttpMethod.PATCH,
                    new HttpEntity<>(request, adminHeaders()), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void 존재하지_않는_템플릿이면_404_Not_Found를_반환한다() {
            // act
            AdminCouponTemplateRequest.UpdateTemplateRequest request =
                    new AdminCouponTemplateRequest.UpdateTemplateRequest(
                            "수정", "설명", "FIXED", 1000, null, 5000);
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    "/api-admin/v1/coupon-templates/999", HttpMethod.PATCH,
                    new HttpEntity<>(request, adminHeaders()), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("DELETE /api-admin/v1/coupon-templates/{templateId}")
    @Nested
    class 템플릿_삭제 {

        @Test
        void 삭제에_성공하면_200_OK를_반환한다() {
            // arrange
            CouponTemplate template = createTemplate();

            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    "/api-admin/v1/coupon-templates/" + template.getId(), HttpMethod.DELETE,
                    new HttpEntity<>(adminHeaders()), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void 존재하지_않는_템플릿이면_404_Not_Found를_반환한다() {
            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    "/api-admin/v1/coupon-templates/999", HttpMethod.DELETE,
                    new HttpEntity<>(adminHeaders()), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
