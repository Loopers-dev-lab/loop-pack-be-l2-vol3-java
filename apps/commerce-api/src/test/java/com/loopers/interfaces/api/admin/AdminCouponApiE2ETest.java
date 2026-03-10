package com.loopers.interfaces.api.admin;

import com.loopers.domain.common.Money;
import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.domain.coupon.DiscountType;
import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.domain.coupon.IssuedCouponRepository;
import com.loopers.interfaces.api.ApiResponse;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminCouponApiE2ETest {

    private static final String ADMIN_COUPONS_ENDPOINT = "/api/admin/v1/coupons";
    private static final String ADMIN_LDAP = "admin-test";

    @Autowired
    private TestRestTemplate testRestTemplate;
    @Autowired
    private CouponRepository couponRepository;
    @Autowired
    private IssuedCouponRepository issuedCouponRepository;
    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("POST /api/admin/v1/coupons")
    class CreateCouponTest {

        @Test
        @DisplayName("쿠폰을 생성할 수 있다")
        void create_success() {
            AdminCouponDto.CreateRequest request = new AdminCouponDto.CreateRequest(
                    "1000원 할인", DiscountType.FIXED, BigDecimal.valueOf(1000),
                    BigDecimal.valueOf(5000), null, 100,
                    ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(30)
            );
            HttpEntity<AdminCouponDto.CreateRequest> httpEntity = createAdminHttpEntity(request);

            ResponseEntity<ApiResponse<AdminCouponDto.CouponResponse>> response = testRestTemplate.exchange(
                    ADMIN_COUPONS_ENDPOINT, HttpMethod.POST, httpEntity, new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().name()).isEqualTo("1000원 할인"),
                    () -> assertThat(response.getBody().data().discountType()).isEqualTo(DiscountType.FIXED),
                    () -> assertThat(response.getBody().data().totalQuantity()).isEqualTo(100)
            );
        }

        @Test
        @DisplayName("Admin 인증 없이 요청하면 401 응답을 받는다")
        void create_unauthorized() {
            AdminCouponDto.CreateRequest request = new AdminCouponDto.CreateRequest(
                    "테스트", DiscountType.FIXED, BigDecimal.valueOf(1000),
                    null, null, 100,
                    ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(30)
            );
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<AdminCouponDto.CreateRequest> httpEntity = new HttpEntity<>(request, headers);

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ADMIN_COUPONS_ENDPOINT, HttpMethod.POST, httpEntity, new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Nested
    @DisplayName("GET /api/admin/v1/coupons")
    class GetAllCouponsTest {

        @Test
        @DisplayName("전체 쿠폰 목록을 페이지네이션으로 조회할 수 있다")
        void getAll_success() {
            createTestCoupon("쿠폰A");
            createTestCoupon("쿠폰B");
            createTestCoupon("쿠폰C");
            HttpEntity<Void> httpEntity = createAdminHttpEntity(null);

            ResponseEntity<ApiResponse<AdminCouponDto.CouponListResponse>> response = testRestTemplate.exchange(
                    ADMIN_COUPONS_ENDPOINT + "?page=0&size=2", HttpMethod.GET, httpEntity,
                    new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().coupons()).hasSize(2),
                    () -> assertThat(response.getBody().data().totalElements()).isEqualTo(3),
                    () -> assertThat(response.getBody().data().totalPages()).isEqualTo(2),
                    () -> assertThat(response.getBody().data().page()).isEqualTo(0),
                    () -> assertThat(response.getBody().data().size()).isEqualTo(2)
            );
        }
    }

    @Nested
    @DisplayName("GET /api/admin/v1/coupons/{couponId}/issued")
    class GetIssuedCouponsTest {

        @Test
        @DisplayName("쿠폰 발급 내역을 페이지네이션으로 조회할 수 있다")
        void getIssuedCoupons_success() {
            Coupon coupon = createTestCoupon("테스트 쿠폰");
            issuedCouponRepository.save(IssuedCoupon.create(coupon, 1L));
            issuedCouponRepository.save(IssuedCoupon.create(coupon, 2L));
            issuedCouponRepository.save(IssuedCoupon.create(coupon, 3L));
            HttpEntity<Void> httpEntity = createAdminHttpEntity(null);

            ResponseEntity<ApiResponse<AdminCouponDto.IssuedCouponListResponse>> response = testRestTemplate.exchange(
                    ADMIN_COUPONS_ENDPOINT + "/" + coupon.getId() + "/issued?page=0&size=2",
                    HttpMethod.GET, httpEntity, new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().issuedCoupons()).hasSize(2),
                    () -> assertThat(response.getBody().data().totalElements()).isEqualTo(3),
                    () -> assertThat(response.getBody().data().totalPages()).isEqualTo(2),
                    () -> assertThat(response.getBody().data().page()).isEqualTo(0),
                    () -> assertThat(response.getBody().data().size()).isEqualTo(2)
            );
        }
    }

    @Nested
    @DisplayName("DELETE /api/admin/v1/coupons/{id}")
    class DeleteCouponTest {

        @Test
        @DisplayName("쿠폰을 삭제할 수 있다")
        void delete_success() {
            Coupon coupon = createTestCoupon("삭제될 쿠폰");
            HttpEntity<Void> httpEntity = createAdminHttpEntity(null);

            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                    ADMIN_COUPONS_ENDPOINT + "/" + coupon.getId(),
                    HttpMethod.DELETE, httpEntity, new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    private Coupon createTestCoupon(String name) {
        return couponRepository.save(Coupon.create(
                name, DiscountType.FIXED, Money.of(1000L), Money.of(5000L), null, 100,
                ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(30)
        ));
    }

    private <T> HttpEntity<T> createAdminHttpEntity(T body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Loopers-Ldap", ADMIN_LDAP);
        return new HttpEntity<>(body, headers);
    }
}
