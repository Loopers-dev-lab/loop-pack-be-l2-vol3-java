package com.loopers.interfaces.coupon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.loopers.domain.coupon.CouponDiscountType;
import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.OwnedCouponModel;
import com.loopers.infrastructure.coupon.CouponJpaRepository;
import com.loopers.infrastructure.coupon.OwnedCouponJpaRepository;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.coupon.dto.CouponV1Dto;
import com.loopers.interfaces.user.dto.UserV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

@DisplayName("Coupon V1 API E2E 테스트")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CouponV1ApiE2ETest {

    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";

    private final TestRestTemplate testRestTemplate;
    private final CouponJpaRepository couponJpaRepository;
    private final OwnedCouponJpaRepository ownedCouponJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    private Long userId;

    @Autowired
    public CouponV1ApiE2ETest(
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

    @BeforeEach
    void setUp() {
        UserV1Dto.SignupRequest signupRequest = new UserV1Dto.SignupRequest(
                "testuser1", "Test1234!", "홍길동", "19900101", "test@example.com");
        ResponseEntity<ApiResponse<UserV1Dto.SignupResponse>> signupResponse =
                testRestTemplate.exchange(
                        "/api/v1/users/signup", HttpMethod.POST,
                        new HttpEntity<>(signupRequest),
                        new ParameterizedTypeReference<>() {});
        userId = signupResponse.getBody().data().id();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_LOGIN_ID, "testuser1");
        headers.set(HEADER_LOGIN_PW, "Test1234!");
        return headers;
    }

    private CouponModel saveCoupon(String name, CouponDiscountType type, long value) {
        return couponJpaRepository.save(
                CouponModel.create(name, type, value, null, 100,
                        ZonedDateTime.now().plusMonths(3)));
    }

    @DisplayName("POST /api/v1/coupons/{couponId}/issue")
    @Nested
    class IssueCoupon {

        @DisplayName("쿠폰을 발급받으면, SUCCESS 응답을 반환한다.")
        @Test
        void issue_returnsSuccess() {
            // arrange
            CouponModel coupon = saveCoupon("테스트 쿠폰", CouponDiscountType.FIXED, 5000);

            // act
            ResponseEntity<ApiResponse<Object>> response =
                    testRestTemplate.exchange(
                            "/api/v1/coupons/" + coupon.getId() + "/issue",
                            HttpMethod.POST,
                            new HttpEntity<>(null, authHeaders()),
                            new ParameterizedTypeReference<>() {});

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(ownedCouponJpaRepository.findAll()).hasSize(1));
        }

        @DisplayName("이미 발급받은 쿠폰을 다시 발급받으면, 409 응답을 반환한다.")
        @Test
        void issue_returnsConflict_whenAlreadyIssued() {
            // arrange
            CouponModel coupon = saveCoupon("테스트 쿠폰", CouponDiscountType.FIXED, 5000);
            coupon.issue();
            couponJpaRepository.save(coupon);
            ownedCouponJpaRepository.save(OwnedCouponModel.create(coupon, userId));

            // act
            ResponseEntity<ApiResponse<Object>> response =
                    testRestTemplate.exchange(
                            "/api/v1/coupons/" + coupon.getId() + "/issue",
                            HttpMethod.POST,
                            new HttpEntity<>(null, authHeaders()),
                            new ParameterizedTypeReference<>() {});

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @DisplayName("존재하지 않는 쿠폰을 발급받으면, 404 응답을 반환한다.")
        @Test
        void issue_returnsNotFound() {
            // act
            ResponseEntity<ApiResponse<Object>> response =
                    testRestTemplate.exchange(
                            "/api/v1/coupons/999/issue",
                            HttpMethod.POST,
                            new HttpEntity<>(null, authHeaders()),
                            new ParameterizedTypeReference<>() {});

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("GET /api/v1/users/me/coupons")
    @Nested
    class MyOwnedCoupons {

        @DisplayName("내 쿠폰 목록을 반환한다.")
        @Test
        void myOwnedCoupons_returnsList() {
            // arrange
            CouponModel coupon = saveCoupon("테스트 쿠폰", CouponDiscountType.RATE, 10);
            coupon.issue();
            couponJpaRepository.save(coupon);
            ownedCouponJpaRepository.save(OwnedCouponModel.create(coupon, userId));

            // act
            ResponseEntity<ApiResponse<CouponV1Dto.OwnedCouponListResponse>> response =
                    testRestTemplate.exchange(
                            "/api/v1/users/me/coupons", HttpMethod.GET,
                            new HttpEntity<>(authHeaders()),
                            new ParameterizedTypeReference<>() {});

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().items()).hasSize(1),
                    () -> assertThat(response.getBody().data().items().get(0).couponName())
                            .isEqualTo("테스트 쿠폰"),
                    () -> assertThat(response.getBody().data().items().get(0).status())
                            .isEqualTo("AVAILABLE"));
        }
    }
}
