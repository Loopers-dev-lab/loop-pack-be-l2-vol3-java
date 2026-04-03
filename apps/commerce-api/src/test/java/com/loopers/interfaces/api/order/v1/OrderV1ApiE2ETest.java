package com.loopers.interfaces.api.order.v1;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;

import static com.loopers.interfaces.api.coupon.v1.CouponSteps.createCoupon;
import static com.loopers.interfaces.api.coupon.v1.CouponSteps.issueCoupon;
import static com.loopers.interfaces.api.order.v1.OrderSteps.createOrder;
import static com.loopers.interfaces.api.order.v1.OrderSteps.getMyOrder;
import static com.loopers.interfaces.api.order.v1.OrderSteps.getMyOrders;
import static com.loopers.interfaces.api.user.v1.UserSteps.signUp;
import static com.loopers.support.E2ETestHelper.assertErrorResponse;
import static com.loopers.support.E2ETestHelper.userAuthHeaders;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.coupon.CouponType;
import com.loopers.interfaces.api.coupon.v1.CouponDto;
import com.loopers.support.error.ErrorType;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.util.UriComponentsBuilder;

import com.loopers.domain.coupon.CouponRepository;
import com.loopers.domain.coupon.OwnedCouponFixture;
import com.loopers.domain.coupon.OwnedCouponRepository;
import com.loopers.interfaces.api.brand.v1.BrandDto;
import com.loopers.interfaces.api.brand.v1.BrandSteps;
import com.loopers.interfaces.api.product.v1.ProductDto;
import com.loopers.interfaces.api.product.v1.ProductSteps;
import com.loopers.interfaces.api.user.v1.UserV1Dto;
import com.loopers.support.BaseE2ETest;

class OrderV1ApiE2ETest extends BaseE2ETest {

    private static final String ENTRY_TOKEN_KEY_PREFIX = "entry-token:";
    private static final String HEADER_ENTRY_TOKEN = "X-Entry-Token";

    @Autowired
    private OwnedCouponRepository ownedCouponRepository;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private RedisTemplate<String, String> redisTemplate;

    private HttpHeaders userHeaders;
    private Long userId;
    private Long productId;

    @BeforeEach
    void setUp() {
        var loginId = "testuser1";
        var loginPw = "Password1!";
        var signUpResponse = signUp(testRestTemplate, new UserV1Dto.SignUpRequest(loginId, loginPw, "테스트", "2000-01-01", "test@test.com"));
        userId = signUpResponse.getBody().data().id();
        userHeaders = userAuthHeaders(loginId, loginPw);

        var brandId = BrandSteps.createBrand(
                testRestTemplate,
                new BrandDto.CreateBrandRequest("테스트 브랜드", "https://example.com/logo.png", "브랜드 설명")
        );
        productId = ProductSteps.createProduct(
                testRestTemplate,
                new ProductDto.CreateProductRequest(brandId, "테스트 상품", "https://example.com/thumb.png", 10000L, 100L, "상품 설명")
        );
    }

    private String seedEntryToken(Long targetUserId) {
        String token = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(ENTRY_TOKEN_KEY_PREFIX + targetUserId, token, Duration.ofSeconds(120));
        return token;
    }

    private HttpHeaders headersWithEntryToken(String entryToken) {
        var headers = new HttpHeaders(userHeaders);
        headers.set(HEADER_ENTRY_TOKEN, entryToken);
        return headers;
    }

    @DisplayName("POST /api/v1/orders")
    @Nested
    class PlaceOrder {

        @DisplayName("유효한 상품과 수량으로 주문하면, 주문이 생성된다.")
        @Test
        void createsOrder_whenValidRequest() {
            // arrange
            var request = new OrderDto.CreateOrderRequest(
                    List.of(new OrderDto.OrderItemRequest(productId, 2L)),
                    null
            );

            // act
            var response = createOrder(testRestTemplate, request, userHeaders);

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                    () -> assertThat(response.getBody().data().orderId()).isNotNull(),
                    () -> assertThat(response.getBody().data().orderKey()).isNotBlank()
            );
        }

        @DisplayName("쿠폰을 적용하여 주문하면, 할인이 적용된 주문이 생성된다.")
        @Test
        void createsOrderWithCouponDiscount() {
            // arrange
            var couponId = createCoupon(testRestTemplate, new CouponDto.CreateCouponRequest(
                    "5000원 할인", CouponType.FIXED, 5000L, null, 10000L, ZonedDateTime.now().plusDays(30), 10000
            ));
            // Phase 4: 쿠폰 발급이 비동기(Kafka)로 전환되어, 테스트 데이터 셋업은 서비스 직접 호출
            var coupon = couponRepository.findById(couponId).orElseThrow();
            var ownedCouponId = ownedCouponRepository.save(OwnedCouponFixture.createOwnedCoupon(coupon, 1L)).getId();

            var request = new OrderDto.CreateOrderRequest(
                    List.of(new OrderDto.OrderItemRequest(productId, 2L)),
                    ownedCouponId
            );

            // act
            var response = createOrder(testRestTemplate, request, userHeaders);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

            var orderKey = response.getBody().data().orderKey();
            var detail = getMyOrder(testRestTemplate, orderKey, userHeaders).getBody().data();
            assertAll(
                    () -> assertThat(detail.originalTotalPrice()).isEqualTo(20000L),
                    () -> assertThat(detail.discountAmount()).isEqualTo(5000L),
                    () -> assertThat(detail.totalPrice()).isEqualTo(15000L)
            );
        }

        @DisplayName("쿠폰 없이 주문하면, discountAmount=0이고 originalTotalPrice=totalPrice이다.")
        @Test
        void createsOrderWithoutCoupon() {
            // arrange
            var request = new OrderDto.CreateOrderRequest(
                    List.of(new OrderDto.OrderItemRequest(productId, 2L)),
                    null
            );

            // act
            var response = createOrder(testRestTemplate, request, userHeaders);

            // assert
            var orderKey = response.getBody().data().orderKey();
            var detail = getMyOrder(testRestTemplate, orderKey, userHeaders).getBody().data();
            assertAll(
                    () -> assertThat(detail.originalTotalPrice()).isEqualTo(20000L),
                    () -> assertThat(detail.discountAmount()).isEqualTo(0L),
                    () -> assertThat(detail.totalPrice()).isEqualTo(20000L)
            );
        }

        @DisplayName("존재하지 않는 쿠폰으로 주문하면, 실패한다.")
        @Test
        void failsOrder_whenOwnedCouponNotFound() {
            // arrange
            var request = new OrderDto.CreateOrderRequest(
                    List.of(new OrderDto.OrderItemRequest(productId, 2L)),
                    999L
            );

            // act
            var response = createOrder(testRestTemplate, request, userHeaders);

            // assert
            assertErrorResponse(response, HttpStatus.NOT_FOUND, ErrorType.OWNED_COUPON_NOT_FOUND);
        }

        @DisplayName("타인 소유 쿠폰으로 주문하면, 실패한다.")
        @Test
        void failsOrder_whenCouponBelongsToOtherUser() {
            // arrange
            var couponId = createCoupon(testRestTemplate, new CouponDto.CreateCouponRequest(
                    "할인 쿠폰", CouponType.FIXED, 5000L, null, 10000L, ZonedDateTime.now().plusDays(30), 10000
            ));

            signUp(testRestTemplate, new UserV1Dto.SignUpRequest("otheruser2", "Password1!", "다른유저", "1990-01-01", "other2@test.com"));
            // Phase 4: 쿠폰 발급이 비동기(Kafka)로 전환되어, 테스트 데이터 셋업은 서비스 직접 호출
            var coupon = couponRepository.findById(couponId).orElseThrow();
            var otherOwnedCouponId = ownedCouponRepository.save(OwnedCouponFixture.createOwnedCoupon(coupon, 2L)).getId();

            var request = new OrderDto.CreateOrderRequest(
                    List.of(new OrderDto.OrderItemRequest(productId, 2L)),
                    otherOwnedCouponId
            );

            // act
            var response = createOrder(testRestTemplate, request, userHeaders);

            // assert
            assertErrorResponse(response, HttpStatus.FORBIDDEN, ErrorType.FORBIDDEN_COUPON_ACCESS);
        }

        @DisplayName("최소 주문 금액 미달 시 쿠폰으로 주문하면, 실패한다.")
        @Test
        void failsOrder_whenMinOrderPriceNotMet() {
            // arrange
            var couponId = createCoupon(testRestTemplate, new CouponDto.CreateCouponRequest(
                    "할인 쿠폰", CouponType.FIXED, 5000L, null, 50000L, ZonedDateTime.now().plusDays(30), 10000
            ));
            // Phase 4: 쿠폰 발급이 비동기(Kafka)로 전환되어, 테스트 데이터 셋업은 서비스 직접 호출
            var coupon = couponRepository.findById(couponId).orElseThrow();
            var ownedCouponId = ownedCouponRepository.save(OwnedCouponFixture.createOwnedCoupon(coupon, 1L)).getId();

            // 상품 10000원 × 2 = 20000원 < 50000원
            var request = new OrderDto.CreateOrderRequest(
                    List.of(new OrderDto.OrderItemRequest(productId, 2L)),
                    ownedCouponId
            );

            // act
            var response = createOrder(testRestTemplate, request, userHeaders);

            // assert
            assertErrorResponse(response, HttpStatus.BAD_REQUEST, ErrorType.COUPON_MIN_ORDER_PRICE_NOT_MET);
        }

        @DisplayName("유효한 진입 토큰 헤더로 주문하면, 주문이 생성된다.")
        @Test
        void createsOrder_whenValidEntryToken() {
            // arrange
            var entryToken = seedEntryToken(userId);
            var request = new OrderDto.CreateOrderRequest(
                    List.of(new OrderDto.OrderItemRequest(productId, 2L)),
                    null
            );

            // act
            var response = createOrder(testRestTemplate, request, headersWithEntryToken(entryToken));

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                    () -> assertThat(response.getBody().data().orderId()).isNotNull()
            );
        }

        @DisplayName("진입 토큰이 불일치하면, 403 Forbidden을 반환한다.")
        @Test
        void failsOrder_whenTokenMismatch() {
            // arrange
            seedEntryToken(userId);
            var request = new OrderDto.CreateOrderRequest(
                    List.of(new OrderDto.OrderItemRequest(productId, 2L)),
                    null
            );

            // act
            var response = createOrder(testRestTemplate, request, headersWithEntryToken("wrong-token"));

            // assert
            assertErrorResponse(response, HttpStatus.FORBIDDEN, ErrorType.INVALID_ENTRY_TOKEN);
        }

        @DisplayName("Redis에 진입 토큰이 없으면, 403 Forbidden을 반환한다.")
        @Test
        void failsOrder_whenNoTokenInRedis() {
            // arrange
            var request = new OrderDto.CreateOrderRequest(
                    List.of(new OrderDto.OrderItemRequest(productId, 2L)),
                    null
            );

            // act
            var response = createOrder(testRestTemplate, request, headersWithEntryToken("any-token"));

            // assert
            assertErrorResponse(response, HttpStatus.FORBIDDEN, ErrorType.INVALID_ENTRY_TOKEN);
        }

        @DisplayName("주문 성공 후 토큰이 삭제되면, 동일 토큰으로 재주문 시 403 Forbidden을 반환한다.")
        @Test
        void failsOrder_whenTokenDeletedAfterOrderSuccess() {
            // arrange
            var entryToken = seedEntryToken(userId);
            var request = new OrderDto.CreateOrderRequest(
                    List.of(new OrderDto.OrderItemRequest(productId, 2L)),
                    null
            );
            var headers = headersWithEntryToken(entryToken);
            createOrder(testRestTemplate, request, headers);

            // AFTER_COMMIT + @Async로 토큰 삭제 대기
            await().atMost(5, SECONDS).untilAsserted(() ->
                    assertThat(redisTemplate.opsForValue().get(ENTRY_TOKEN_KEY_PREFIX + userId)).isNull()
            );

            // act - 토큰 삭제 후 동일 토큰으로 재주문
            var secondResponse = createOrder(testRestTemplate, request, headers);

            // assert
            assertErrorResponse(secondResponse, HttpStatus.FORBIDDEN, ErrorType.INVALID_ENTRY_TOKEN);
        }
    }

    @DisplayName("GET /api/v1/orders")
    @Nested
    class ReadMyOrders {

        private static final String ORDER_ENDPOINT = "/api/v1/orders";

        @DisplayName("주문이 존재하면, 주문 목록이 반환된다.")
        @Test
        void returnsOrderList_whenOrdersExist() {
            // arrange
            createOrder(testRestTemplate,
                    new OrderDto.CreateOrderRequest(List.of(new OrderDto.OrderItemRequest(productId, 2L)), null),
                    userHeaders);

            var today = LocalDate.now();
            var url = UriComponentsBuilder.fromPath(ORDER_ENDPOINT)
                    .queryParam("startDate", today.toString())
                    .queryParam("endDate", today.toString())
                    .toUriString();

            // act
            var response = getMyOrders(testRestTemplate, url, userHeaders);

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().content()).hasSize(1),
                    () -> assertThat(response.getBody().data().content().get(0).name()).isEqualTo("테스트 상품"),
                    () -> assertThat(response.getBody().data().hasNext()).isFalse()
            );
        }

        @DisplayName("날짜 범위 밖이면, 빈 목록이 반환된다.")
        @Test
        void returnsEmptyPage_whenNoOrdersInDateRange() {
            // arrange
            createOrder(testRestTemplate,
                    new OrderDto.CreateOrderRequest(List.of(new OrderDto.OrderItemRequest(productId, 1L)), null),
                    userHeaders);

            var url = UriComponentsBuilder.fromPath(ORDER_ENDPOINT)
                    .queryParam("startDate", "2020-01-01")
                    .queryParam("endDate", "2020-01-02")
                    .toUriString();

            // act
            var response = getMyOrders(testRestTemplate, url, userHeaders);

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().content()).isEmpty(),
                    () -> assertThat(response.getBody().data().hasNext()).isFalse()
            );
        }

        @DisplayName("startDate가 누락되면, 400 Bad Request를 반환한다.")
        @Test
        void returnsBadRequest_whenStartDateMissing() {
            // arrange
            var url = UriComponentsBuilder.fromPath(ORDER_ENDPOINT)
                    .queryParam("endDate", "2026-01-01")
                    .toUriString();

            // act
            var response = getMyOrders(testRestTemplate, url, userHeaders);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("endDate가 누락되면, 400 Bad Request를 반환한다.")
        @Test
        void returnsBadRequest_whenEndDateMissing() {
            // arrange
            var url = UriComponentsBuilder.fromPath(ORDER_ENDPOINT)
                    .queryParam("startDate", "2026-01-01")
                    .toUriString();

            // act
            var response = getMyOrders(testRestTemplate, url, userHeaders);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("GET /api/v1/orders/{orderKey}")
    @Nested
    class ReadMyOrderDetail {

        @DisplayName("주문 상세 정보를 조회하면, 주문 정보와 주문 항목이 반환된다.")
        @Test
        void returnsOrderDetail_whenValidOrderKey() {
            // arrange
            var request = new OrderDto.CreateOrderRequest(
                    List.of(new OrderDto.OrderItemRequest(productId, 2L)),
                    null
            );
            var orderKey = createOrder(testRestTemplate, request, userHeaders).getBody().data().orderKey();

            // act
            var response = getMyOrder(testRestTemplate, orderKey, userHeaders);

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().orderKey()).isEqualTo(orderKey),
                    () -> assertThat(response.getBody().data().name()).isEqualTo("테스트 상품"),
                    () -> assertThat(response.getBody().data().totalPrice()).isEqualTo(20000L),
                    () -> assertThat(response.getBody().data().orderItems()).hasSize(1),
                    () -> assertThat(response.getBody().data().orderItems().get(0).productName()).isEqualTo("테스트 상품"),
                    () -> assertThat(response.getBody().data().orderItems().get(0).quantity()).isEqualTo(2L),
                    () -> assertThat(response.getBody().data().orderItems().get(0).subtotal()).isEqualTo(20000L)
            );
        }

        @DisplayName("존재하지 않는 주문을 조회하면, 404 Not Found를 반환한다.")
        @Test
        void returnsNotFound_whenOrderDoesNotExist() {
            // act
            var response = getMyOrder(testRestTemplate, "non-existent-key", userHeaders);

            // assert
            assertErrorResponse(response, HttpStatus.NOT_FOUND, ErrorType.ORDER_NOT_FOUND);
        }

        @DisplayName("다른 사용자의 주문을 조회하면, 403 Forbidden을 반환한다.")
        @Test
        void returnsForbidden_whenOtherUsersOrder() {
            // arrange
            var request = new OrderDto.CreateOrderRequest(
                    List.of(new OrderDto.OrderItemRequest(productId, 1L)),
                    null
            );
            var orderKey = createOrder(testRestTemplate, request, userHeaders).getBody().data().orderKey();

            signUp(testRestTemplate, new UserV1Dto.SignUpRequest("otheruser", "Password1!", "다른유저", "1995-05-05", "other@test.com"));
            var otherHeaders = userAuthHeaders("otheruser", "Password1!");

            // act
            var response = getMyOrder(testRestTemplate, orderKey, otherHeaders);

            // assert
            assertErrorResponse(response, HttpStatus.FORBIDDEN, ErrorType.FORBIDDEN_ORDER_ACCESS);
        }
    }
}
