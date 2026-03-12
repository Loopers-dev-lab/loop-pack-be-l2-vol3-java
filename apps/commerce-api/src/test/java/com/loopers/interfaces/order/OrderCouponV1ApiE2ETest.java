package com.loopers.interfaces.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.coupon.CouponDiscountType;
import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.OwnedCouponModel;
import com.loopers.domain.product.ProductModel;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.coupon.CouponJpaRepository;
import com.loopers.infrastructure.coupon.OwnedCouponJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.order.dto.OrderRequest;
import com.loopers.interfaces.order.dto.OrderResponse;
import com.loopers.interfaces.user.dto.UserV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import java.time.ZonedDateTime;
import java.util.List;
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

@DisplayName("Order + Coupon V1 API E2E 테스트")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderCouponV1ApiE2ETest {

    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";
    private static final String ORDER_ENDPOINT = "/api/v1/orders";

    private final TestRestTemplate testRestTemplate;
    private final BrandJpaRepository brandJpaRepository;
    private final ProductJpaRepository productJpaRepository;
    private final CouponJpaRepository couponJpaRepository;
    private final OwnedCouponJpaRepository ownedCouponJpaRepository;
    private final UserJpaRepository userJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    private Long userId;
    private Long productId;

    @Autowired
    public OrderCouponV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        BrandJpaRepository brandJpaRepository,
        ProductJpaRepository productJpaRepository,
        CouponJpaRepository couponJpaRepository,
        OwnedCouponJpaRepository ownedCouponJpaRepository,
        UserJpaRepository userJpaRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.brandJpaRepository = brandJpaRepository;
        this.productJpaRepository = productJpaRepository;
        this.couponJpaRepository = couponJpaRepository;
        this.ownedCouponJpaRepository = ownedCouponJpaRepository;
        this.userJpaRepository = userJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @BeforeEach
    void setUp() {
        // 사용자 등록
        UserV1Dto.SignupRequest signupRequest = new UserV1Dto.SignupRequest(
                "testuser1", "Test1234!", "홍길동", "19900101", "test@example.com");
        ResponseEntity<ApiResponse<UserV1Dto.SignupResponse>> signupResponse =
                testRestTemplate.exchange(
                        "/api/v1/users/signup", HttpMethod.POST,
                        new HttpEntity<>(signupRequest),
                        new ParameterizedTypeReference<>() {});
        userId = signupResponse.getBody().data().id();

        // 포인트 충전
        com.loopers.domain.user.UserModel user = userJpaRepository.findById(userId).orElseThrow();
        user.addPoint(10_000_000);
        userJpaRepository.save(user);

        // 브랜드 + 상품
        BrandModel brand = brandJpaRepository.save(BrandModel.create("ACNE STUDIOS"));
        ProductModel product = productJpaRepository.save(
                ProductModel.create(brand.getId(), "오버사이즈 코트", 50000, 100));
        productId = product.getId();
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

    private OwnedCouponModel issueOwnedCoupon(CouponDiscountType type, long value,
                                               Long minOrderAmount) {
        CouponModel coupon = couponJpaRepository.save(
                CouponModel.create("테스트 쿠폰", type, value, minOrderAmount, 100,
                        ZonedDateTime.now().plusMonths(3)));
        coupon.issue();
        couponJpaRepository.save(coupon);
        return ownedCouponJpaRepository.save(OwnedCouponModel.create(coupon, userId));
    }

    private OrderRequest.Create orderRequest(Long couponId) {
        return new OrderRequest.Create(
                List.of(new OrderRequest.OrderItemRequest(productId, 2, 50000)),
                couponId);
    }

    @DisplayName("POST /api/v1/orders — 쿠폰 적용 주문")
    @Nested
    class CreateOrderWithCoupon {

        @DisplayName("★ 정액(FIXED) 할인 쿠폰이 적용된 주문을 생성한다.")
        @Test
        void fixedDiscount_appliedCorrectly() {
            // arrange
            OwnedCouponModel owned = issueOwnedCoupon(CouponDiscountType.FIXED, 5000, null);

            // act
            ResponseEntity<ApiResponse<OrderResponse.OrderSummary>> response =
                    testRestTemplate.exchange(
                            ORDER_ENDPOINT, HttpMethod.POST,
                            new HttpEntity<>(orderRequest(owned.getId()), authHeaders()),
                            new ParameterizedTypeReference<>() {});

            // assert — 50000 * 2 = 100000, 할인 5000 → totalPrice 95000
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().originalTotalPrice()).isEqualTo(100000),
                    () -> assertThat(response.getBody().data().discountAmount()).isEqualTo(5000),
                    () -> assertThat(response.getBody().data().totalPrice()).isEqualTo(95000));
        }

        @DisplayName("★ 정률(RATE) 할인 쿠폰이 적용된 주문을 생성한다.")
        @Test
        void rateDiscount_appliedCorrectly() {
            // arrange — 10% 할인
            OwnedCouponModel owned = issueOwnedCoupon(CouponDiscountType.RATE, 10, null);

            // act
            ResponseEntity<ApiResponse<OrderResponse.OrderSummary>> response =
                    testRestTemplate.exchange(
                            ORDER_ENDPOINT, HttpMethod.POST,
                            new HttpEntity<>(orderRequest(owned.getId()), authHeaders()),
                            new ParameterizedTypeReference<>() {});

            // assert — 100000 * 10% = 10000 할인 → totalPrice 90000
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().originalTotalPrice()).isEqualTo(100000),
                    () -> assertThat(response.getBody().data().discountAmount()).isEqualTo(10000),
                    () -> assertThat(response.getBody().data().totalPrice()).isEqualTo(90000));
        }

        @DisplayName("★ 이미 사용된 쿠폰으로 주문하면, 400 응답을 반환한다.")
        @Test
        void alreadyUsedCoupon_returnsBadRequest() {
            // arrange — 주문 API로 쿠폰을 사용시킨 뒤, 같은 쿠폰으로 재주문
            OwnedCouponModel owned = issueOwnedCoupon(CouponDiscountType.FIXED, 5000, null);
            testRestTemplate.exchange(
                    ORDER_ENDPOINT, HttpMethod.POST,
                    new HttpEntity<>(orderRequest(owned.getId()), authHeaders()),
                    new ParameterizedTypeReference<ApiResponse<OrderResponse.OrderSummary>>() {});

            // act
            ResponseEntity<ApiResponse<Object>> response =
                    testRestTemplate.exchange(
                            ORDER_ENDPOINT, HttpMethod.POST,
                            new HttpEntity<>(orderRequest(owned.getId()), authHeaders()),
                            new ParameterizedTypeReference<>() {});

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("쿠폰 없이 주문하면, 기존 흐름 그대로 동작한다.")
        @Test
        void noCoupon_worksNormally() {
            // act
            ResponseEntity<ApiResponse<OrderResponse.OrderSummary>> response =
                    testRestTemplate.exchange(
                            ORDER_ENDPOINT, HttpMethod.POST,
                            new HttpEntity<>(orderRequest(null), authHeaders()),
                            new ParameterizedTypeReference<>() {});

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().totalPrice()).isEqualTo(100000),
                    () -> assertThat(response.getBody().data().discountAmount()).isEqualTo(0));
        }

        @DisplayName("최소 주문 금액 미달 시, 400 응답을 반환한다.")
        @Test
        void minOrderAmountNotMet_returnsBadRequest() {
            // arrange — 최소 주문 금액 200000원
            OwnedCouponModel owned = issueOwnedCoupon(CouponDiscountType.FIXED, 5000, 200000L);

            // act
            ResponseEntity<ApiResponse<Object>> response =
                    testRestTemplate.exchange(
                            ORDER_ENDPOINT, HttpMethod.POST,
                            new HttpEntity<>(orderRequest(owned.getId()), authHeaders()),
                            new ParameterizedTypeReference<>() {});

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("★ 쿠폰 사용 후 해당 쿠폰은 USED 상태가 된다.")
        @Test
        void couponBecomesUsed_afterOrder() {
            // arrange
            OwnedCouponModel owned = issueOwnedCoupon(CouponDiscountType.FIXED, 5000, null);

            // act
            testRestTemplate.exchange(
                    ORDER_ENDPOINT, HttpMethod.POST,
                    new HttpEntity<>(orderRequest(owned.getId()), authHeaders()),
                    new ParameterizedTypeReference<ApiResponse<OrderResponse.OrderSummary>>() {});

            // assert
            OwnedCouponModel updated = ownedCouponJpaRepository.findById(owned.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo("USED");
        }
    }

    @DisplayName("주문 취소 시 쿠폰 복원")
    @Nested
    class CancelOrderWithCoupon {

        @DisplayName("전체 취소 시 쿠폰이 AVAILABLE로 복원된다.")
        @Test
        void fullCancel_restoresCoupon() {
            // arrange — 쿠폰 적용 주문 생성
            OwnedCouponModel owned = issueOwnedCoupon(CouponDiscountType.FIXED, 5000, null);

            ResponseEntity<ApiResponse<OrderResponse.OrderSummary>> orderResponse =
                    testRestTemplate.exchange(
                            ORDER_ENDPOINT, HttpMethod.POST,
                            new HttpEntity<>(orderRequest(owned.getId()), authHeaders()),
                            new ParameterizedTypeReference<>() {});
            Long orderId = orderResponse.getBody().data().orderId();

            // 주문 상세에서 orderItemId 조회
            ResponseEntity<ApiResponse<OrderResponse.OrderDetail>> detailResponse =
                    testRestTemplate.exchange(
                            ORDER_ENDPOINT + "/" + orderId, HttpMethod.GET,
                            new HttpEntity<>(authHeaders()),
                            new ParameterizedTypeReference<>() {});
            Long orderItemId = detailResponse.getBody().data().items().get(0).orderItemId();

            // act — 유일한 아이템 취소 (전체 취소)
            testRestTemplate.exchange(
                    ORDER_ENDPOINT + "/" + orderId + "/items/" + orderItemId + "/cancel",
                    HttpMethod.PATCH,
                    new HttpEntity<>(null, authHeaders()),
                    new ParameterizedTypeReference<ApiResponse<Object>>() {});

            // assert
            OwnedCouponModel updated = ownedCouponJpaRepository.findById(owned.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo("AVAILABLE");
        }
    }
}
