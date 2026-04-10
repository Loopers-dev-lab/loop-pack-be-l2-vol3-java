package com.loopers.interfaces.coupon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.loopers.domain.coupon.CouponDiscountType;
import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.OwnedCouponModel;
import com.loopers.infrastructure.coupon.CouponJpaRepository;
import com.loopers.infrastructure.coupon.OwnedCouponJpaRepository;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.coupon.dto.AdminCouponV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import java.time.ZonedDateTime;
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
import org.springframework.http.ResponseEntity;

@DisplayName("Admin Coupon V1 API E2E 테스트")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminCouponV1ApiE2ETest {

    private static final String ENDPOINT = "/api-admin/v1/coupons";

    private final TestRestTemplate testRestTemplate;
    private final CouponJpaRepository couponJpaRepository;
    private final OwnedCouponJpaRepository ownedCouponJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public AdminCouponV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        CouponJpaRepository couponJpaRepository,
        OwnedCouponJpaRepository ownedCouponJpaRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.couponJpaRepository = couponJpaRepository;
        this.ownedCouponJpaRepository = ownedCouponJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-Ldap", "loopers.admin");
        return headers;
    }

    private CouponModel saveCoupon(String name, CouponDiscountType type, long value) {
        return couponJpaRepository.save(
                CouponModel.create(name, type, value, null, 100,
                        ZonedDateTime.now().plusMonths(3)));
    }

    @DisplayName("POST /api-admin/v1/coupons")
    @Nested
    class Register {

        @DisplayName("쿠폰을 등록하면, 등록된 쿠폰 정보를 반환한다.")
        @Test
        void register_returnsDetail() {
            // arrange
            AdminCouponV1Dto.RegisterRequest request = new AdminCouponV1Dto.RegisterRequest(
                    "신규가입 10% 할인", CouponDiscountType.RATE, 10L,
                    10000L, 1000, ZonedDateTime.now().plusMonths(3));

            // act
            ResponseEntity<ApiResponse<AdminCouponV1Dto.DetailResponse>> response =
                    testRestTemplate.exchange(
                            ENDPOINT, HttpMethod.POST,
                            new HttpEntity<>(request, adminHeaders()),
                            new ParameterizedTypeReference<>() {});

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().name()).isEqualTo("신규가입 10% 할인"),
                    () -> assertThat(response.getBody().data().discountType()).isEqualTo("RATE"),
                    () -> assertThat(response.getBody().data().discountValue()).isEqualTo(10));
        }
    }

    @DisplayName("GET /api-admin/v1/coupons")
    @Nested
    class ListCoupons {

        @DisplayName("쿠폰 목록을 페이지네이션으로 반환한다.")
        @Test
        void list_returnsPageResponse() {
            // arrange
            saveCoupon("쿠폰A", CouponDiscountType.FIXED, 5000);
            saveCoupon("쿠폰B", CouponDiscountType.RATE, 10);

            // act
            ResponseEntity<ApiResponse<AdminCouponV1Dto.ListResponse>> response =
                    testRestTemplate.exchange(
                            ENDPOINT + "?page=0&size=20", HttpMethod.GET,
                            new HttpEntity<>(adminHeaders()),
                            new ParameterizedTypeReference<>() {});

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().totalElements()).isEqualTo(2),
                    () -> assertThat(response.getBody().data().items()).hasSize(2));
        }
    }

    @DisplayName("GET /api-admin/v1/coupons/{couponId}")
    @Nested
    class GetById {

        @DisplayName("쿠폰 상세를 반환한다.")
        @Test
        void getById_returnsDetail() {
            // arrange
            CouponModel coupon = saveCoupon("테스트 쿠폰", CouponDiscountType.FIXED, 3000);

            // act
            ResponseEntity<ApiResponse<AdminCouponV1Dto.DetailResponse>> response =
                    testRestTemplate.exchange(
                            ENDPOINT + "/" + coupon.getId(), HttpMethod.GET,
                            new HttpEntity<>(adminHeaders()),
                            new ParameterizedTypeReference<>() {});

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().name()).isEqualTo("테스트 쿠폰"));
        }

        @DisplayName("존재하지 않는 쿠폰 ID로 조회하면, 404 응답을 반환한다.")
        @Test
        void getById_returnsNotFound() {
            // act
            ResponseEntity<ApiResponse<Object>> response =
                    testRestTemplate.exchange(
                            ENDPOINT + "/999", HttpMethod.GET,
                            new HttpEntity<>(adminHeaders()),
                            new ParameterizedTypeReference<>() {});

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("PUT /api-admin/v1/coupons/{couponId}")
    @Nested
    class Update {

        @DisplayName("쿠폰을 수정하면, SUCCESS 응답을 반환한다.")
        @Test
        void update_returnsSuccess() {
            // arrange
            CouponModel coupon = saveCoupon("수정 전", CouponDiscountType.FIXED, 1000);
            AdminCouponV1Dto.UpdateRequest request =
                    new AdminCouponV1Dto.UpdateRequest("수정 후", ZonedDateTime.now().plusMonths(6));

            // act
            ResponseEntity<ApiResponse<Object>> response =
                    testRestTemplate.exchange(
                            ENDPOINT + "/" + coupon.getId(), HttpMethod.PUT,
                            new HttpEntity<>(request, adminHeaders()),
                            new ParameterizedTypeReference<>() {});

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(couponJpaRepository.findById(coupon.getId()).get().getName())
                            .isEqualTo("수정 후"));
        }
    }

    @DisplayName("DELETE /api-admin/v1/coupons/{couponId}")
    @Nested
    class Delete {

        @DisplayName("쿠폰을 삭제하면, Soft Delete되고 SUCCESS 응답을 반환한다.")
        @Test
        void delete_softDeletes() {
            // arrange
            CouponModel coupon = saveCoupon("삭제 대상", CouponDiscountType.FIXED, 1000);

            // act
            ResponseEntity<ApiResponse<Object>> response =
                    testRestTemplate.exchange(
                            ENDPOINT + "/" + coupon.getId(), HttpMethod.DELETE,
                            new HttpEntity<>(adminHeaders()),
                            new ParameterizedTypeReference<>() {});

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(couponJpaRepository.findById(coupon.getId()).get().getDeletedAt())
                            .isNotNull());
        }
    }

    @DisplayName("GET /api-admin/v1/coupons/{couponId}/issues")
    @Nested
    class Issues {

        @DisplayName("쿠폰 발급 내역을 페이지네이션으로 반환한다.")
        @Test
        void issues_returnsPageResponse() {
            // arrange
            CouponModel coupon = saveCoupon("발급 대상", CouponDiscountType.FIXED, 1000);
            ownedCouponJpaRepository.save(OwnedCouponModel.create(coupon, 100L));

            // act
            ResponseEntity<ApiResponse<AdminCouponV1Dto.IssueListResponse>> response =
                    testRestTemplate.exchange(
                            ENDPOINT + "/" + coupon.getId() + "/issues?page=0&size=20",
                            HttpMethod.GET,
                            new HttpEntity<>(adminHeaders()),
                            new ParameterizedTypeReference<>() {});

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().totalElements()).isEqualTo(1),
                    () -> assertThat(response.getBody().data().items().get(0).userId()).isEqualTo(100L));
        }
    }
}
