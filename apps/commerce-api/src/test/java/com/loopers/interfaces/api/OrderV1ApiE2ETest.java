package com.loopers.interfaces.api;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.domain.coupon.UserCouponStatus;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductStatus;
import com.loopers.domain.user.UserModel;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.coupon.CouponJpaRepository;
import com.loopers.infrastructure.coupon.UserCouponJpaRepository;
import com.loopers.infrastructure.order.OrderJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderV1ApiE2ETest {

    private static final String ENDPOINT_ORDERS = "/api/v1/orders";
    private static final String ENDPOINT_COUPON_ISSUE = "/api/v1/coupons/{couponId}/issue";
    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";

    private final TestRestTemplate testRestTemplate;
    private final UserJpaRepository userJpaRepository;
    private final BrandJpaRepository brandJpaRepository;
    private final ProductJpaRepository productJpaRepository;
    private final OrderJpaRepository orderJpaRepository;
    private final CouponJpaRepository couponJpaRepository;
    private final UserCouponJpaRepository userCouponJpaRepository;
    private final PasswordEncoder passwordEncoder;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public OrderV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        UserJpaRepository userJpaRepository,
        BrandJpaRepository brandJpaRepository,
        ProductJpaRepository productJpaRepository,
        OrderJpaRepository orderJpaRepository,
        CouponJpaRepository couponJpaRepository,
        UserCouponJpaRepository userCouponJpaRepository,
        PasswordEncoder passwordEncoder,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.userJpaRepository = userJpaRepository;
        this.brandJpaRepository = brandJpaRepository;
        this.productJpaRepository = productJpaRepository;
        this.orderJpaRepository = orderJpaRepository;
        this.couponJpaRepository = couponJpaRepository;
        this.userCouponJpaRepository = userCouponJpaRepository;
        this.passwordEncoder = passwordEncoder;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private UserModel createUser() {
        String encodedPassword = passwordEncoder.encode("Test1234!");
        return userJpaRepository.save(
            UserModel.createWithEncodedPassword("testuser", encodedPassword, "홍길동", LocalDate.of(1990, 1, 15), "test@example.com")
        );
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_LOGIN_ID, "testuser");
        headers.set(HEADER_LOGIN_PW, "Test1234!");
        return headers;
    }

    private ProductModel createProduct(BrandModel brand, String name, Long price, int stock) {
        return productJpaRepository.save(new ProductModel(brand, name, price, name + " 설명", stock, ProductStatus.ON_SALE));
    }

    private CouponModel createRateCoupon(Long rateValue, Long minOrderAmount) {
        return couponJpaRepository.save(new CouponModel(
            "주문 할인 쿠폰",
            CouponType.RATE,
            rateValue,
            minOrderAmount,
            ZonedDateTime.now().plusDays(1)
        ));
    }

    @DisplayName("POST /api/v1/orders - 주문 생성")
    @Nested
    class PlaceOrder {

        @DisplayName("정상적인 주문 요청이면, 주문이 생성된다.")
        @Test
        void placesOrder_whenValidRequestIsProvided() {
            // arrange
            createUser();
            BrandModel brand = brandJpaRepository.save(new BrandModel("나이키", "스포츠 의류 및 신발 브랜드"));
            ProductModel product = createProduct(brand, "에어맥스", 150000L, 10);

            PlaceOrderRequest request = new PlaceOrderRequest(
                List.of(new PlaceOrderItemRequest(product.getId(), 2))
            );

            // act
            ParameterizedTypeReference<ApiResponse<OrderDetailResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<OrderDetailResponse>> response = testRestTemplate.exchange(
                ENDPOINT_ORDERS,
                HttpMethod.POST,
                new HttpEntity<>(request, authHeaders()),
                responseType
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().totalAmount()).isEqualTo(300000L),
                () -> assertThat(response.getBody().data().items()).hasSize(1),
                () -> assertThat(response.getBody().data().items().get(0).productName()).isEqualTo("에어맥스"),
                () -> assertThat(response.getBody().data().items().get(0).quantity()).isEqualTo(2),
                () -> assertThat(response.getBody().data().items().get(0).lineTotalAmount()).isEqualTo(300000L)
            );
        }

        @DisplayName("재고가 부족하면, BAD_REQUEST 응답을 받는다.")
        @Test
        void returnsBadRequest_whenStockIsInsufficient() {
            // arrange
            createUser();
            BrandModel brand = brandJpaRepository.save(new BrandModel("나이키", "스포츠 의류 및 신발 브랜드"));
            ProductModel product = createProduct(brand, "에어맥스", 150000L, 1);

            PlaceOrderRequest request = new PlaceOrderRequest(
                List.of(new PlaceOrderItemRequest(product.getId(), 5))
            );

            // act
            ParameterizedTypeReference<ApiResponse<OrderDetailResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<OrderDetailResponse>> response = testRestTemplate.exchange(
                ENDPOINT_ORDERS,
                HttpMethod.POST,
                new HttpEntity<>(request, authHeaders()),
                responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("주문 후 상품 재고가 차감된다.")
        @Test
        void deductsStock_afterOrderIsPlaced() {
            // arrange
            createUser();
            BrandModel brand = brandJpaRepository.save(new BrandModel("나이키", "스포츠 의류 및 신발 브랜드"));
            ProductModel product = createProduct(brand, "에어맥스", 150000L, 10);

            PlaceOrderRequest request = new PlaceOrderRequest(
                List.of(new PlaceOrderItemRequest(product.getId(), 3))
            );

            // act
            ParameterizedTypeReference<ApiResponse<OrderDetailResponse>> responseType = new ParameterizedTypeReference<>() {};
            testRestTemplate.exchange(
                ENDPOINT_ORDERS,
                HttpMethod.POST,
                new HttpEntity<>(request, authHeaders()),
                responseType
            );

            // assert
            ProductModel updatedProduct = productJpaRepository.findById(product.getId()).orElseThrow();
            assertThat(updatedProduct.getStockQuantity()).isEqualTo(7);
        }

        @DisplayName("발급받은 쿠폰으로 주문하면 할인 적용 후 쿠폰이 USED 상태가 된다.")
        @Test
        void appliesCouponAndMarksUsed_whenIssuedCouponIsRequested() {
            // arrange
            createUser();
            BrandModel brand = brandJpaRepository.save(new BrandModel("아디다스", "스포츠 브랜드"));
            ProductModel product = createProduct(brand, "울트라부스트", 150000L, 10);
            CouponModel coupon = createRateCoupon(10L, 10000L);

            ParameterizedTypeReference<ApiResponse<UserCouponIssueResponse>> issueResponseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserCouponIssueResponse>> issueResponse = testRestTemplate.exchange(
                ENDPOINT_COUPON_ISSUE,
                HttpMethod.POST,
                new HttpEntity<>(authHeaders()),
                issueResponseType,
                coupon.getId()
            );
            Long userCouponId = issueResponse.getBody().data().id();

            PlaceOrderRequest request = new PlaceOrderRequest(
                List.of(new PlaceOrderItemRequest(product.getId(), 2)),
                userCouponId
            );

            // act
            ParameterizedTypeReference<ApiResponse<OrderDetailResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<OrderDetailResponse>> response = testRestTemplate.exchange(
                ENDPOINT_ORDERS,
                HttpMethod.POST,
                new HttpEntity<>(request, authHeaders()),
                responseType
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().originalAmount()).isEqualTo(300000L),
                () -> assertThat(response.getBody().data().discountAmount()).isEqualTo(30000L),
                () -> assertThat(response.getBody().data().totalAmount()).isEqualTo(270000L),
                () -> assertThat(response.getBody().data().usedCouponId()).isEqualTo(userCouponId)
            );

            UserCouponModel usedCoupon = userCouponJpaRepository.findById(userCouponId).orElseThrow();
            assertAll(
                () -> assertThat(usedCoupon.getStatus()).isEqualTo(UserCouponStatus.USED),
                () -> assertThat(usedCoupon.getOrderId()).isEqualTo(response.getBody().data().id())
            );
        }

        @DisplayName("이미 사용한 쿠폰으로 재주문하면 BAD_REQUEST 응답을 받는다.")
        @Test
        void returnsBadRequest_whenUsedCouponIsRequestedAgain() {
            // arrange
            createUser();
            BrandModel brand = brandJpaRepository.save(new BrandModel("아디다스", "스포츠 브랜드"));
            ProductModel product = createProduct(brand, "울트라부스트", 150000L, 10);
            CouponModel coupon = createRateCoupon(10L, 10000L);

            ParameterizedTypeReference<ApiResponse<UserCouponIssueResponse>> issueResponseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserCouponIssueResponse>> issueResponse = testRestTemplate.exchange(
                ENDPOINT_COUPON_ISSUE,
                HttpMethod.POST,
                new HttpEntity<>(authHeaders()),
                issueResponseType,
                coupon.getId()
            );
            Long userCouponId = issueResponse.getBody().data().id();

            PlaceOrderRequest firstOrderRequest = new PlaceOrderRequest(
                List.of(new PlaceOrderItemRequest(product.getId(), 1)),
                userCouponId
            );

            ParameterizedTypeReference<ApiResponse<OrderDetailResponse>> responseType = new ParameterizedTypeReference<>() {};
            testRestTemplate.exchange(
                ENDPOINT_ORDERS,
                HttpMethod.POST,
                new HttpEntity<>(firstOrderRequest, authHeaders()),
                responseType
            );

            PlaceOrderRequest secondOrderRequest = new PlaceOrderRequest(
                List.of(new PlaceOrderItemRequest(product.getId(), 1)),
                userCouponId
            );

            // act
            ResponseEntity<ApiResponse<OrderDetailResponse>> secondOrderResponse = testRestTemplate.exchange(
                ENDPOINT_ORDERS,
                HttpMethod.POST,
                new HttpEntity<>(secondOrderRequest, authHeaders()),
                responseType
            );

            // assert
            assertThat(secondOrderResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("GET /api/v1/orders - 내 주문 목록 조회")
    @Nested
    class GetMyOrders {

        @DisplayName("기간 내 주문이 있으면, 주문 목록을 반환한다.")
        @Test
        void returnsMyOrders_whenOrdersExistInPeriod() {
            // arrange
            createUser();
            BrandModel brand = brandJpaRepository.save(new BrandModel("나이키", "스포츠 의류 및 신발 브랜드"));
            ProductModel product = createProduct(brand, "에어맥스", 150000L, 10);

            PlaceOrderRequest request = new PlaceOrderRequest(
                List.of(new PlaceOrderItemRequest(product.getId(), 1))
            );

            ParameterizedTypeReference<ApiResponse<OrderDetailResponse>> placeType = new ParameterizedTypeReference<>() {};
            testRestTemplate.exchange(
                ENDPOINT_ORDERS,
                HttpMethod.POST,
                new HttpEntity<>(request, authHeaders()),
                placeType
            );

            LocalDate today = LocalDate.now();

            // act
            ParameterizedTypeReference<ApiResponse<List<Map<String, Object>>>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<List<Map<String, Object>>>> response = testRestTemplate.exchange(
                ENDPOINT_ORDERS + "?startAt=" + today + "&endAt=" + today,
                HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                responseType
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data()).hasSize(1)
            );
        }

        @DisplayName("기간 외 주문은 목록에 포함되지 않는다.")
        @Test
        void excludesOrdersOutsidePeriod() {
            // arrange
            createUser();
            BrandModel brand = brandJpaRepository.save(new BrandModel("나이키", "스포츠 의류 및 신발 브랜드"));
            ProductModel product = createProduct(brand, "에어맥스", 150000L, 10);

            PlaceOrderRequest request = new PlaceOrderRequest(
                List.of(new PlaceOrderItemRequest(product.getId(), 1))
            );

            ParameterizedTypeReference<ApiResponse<OrderDetailResponse>> placeType = new ParameterizedTypeReference<>() {};
            testRestTemplate.exchange(
                ENDPOINT_ORDERS,
                HttpMethod.POST,
                new HttpEntity<>(request, authHeaders()),
                placeType
            );

            // 과거 날짜로 조회
            LocalDate pastStart = LocalDate.of(2020, 1, 1);
            LocalDate pastEnd = LocalDate.of(2020, 1, 31);

            // act
            ParameterizedTypeReference<ApiResponse<List<Map<String, Object>>>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<List<Map<String, Object>>>> response = testRestTemplate.exchange(
                ENDPOINT_ORDERS + "?startAt=" + pastStart + "&endAt=" + pastEnd,
                HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                responseType
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data()).isEmpty()
            );
        }
    }

    @DisplayName("GET /api/v1/orders/{orderId} - 내 주문 상세 조회")
    @Nested
    class GetMyOrder {

        @DisplayName("내 주문이면, 주문 상세를 반환한다.")
        @Test
        void returnsMyOrder_whenOrderBelongsToUser() {
            // arrange
            createUser();
            BrandModel brand = brandJpaRepository.save(new BrandModel("나이키", "스포츠 의류 및 신발 브랜드"));
            ProductModel product = createProduct(brand, "에어맥스", 150000L, 10);

            PlaceOrderRequest request = new PlaceOrderRequest(
                List.of(new PlaceOrderItemRequest(product.getId(), 2))
            );

            ParameterizedTypeReference<ApiResponse<OrderDetailResponse>> placeType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<OrderDetailResponse>> placeResponse = testRestTemplate.exchange(
                ENDPOINT_ORDERS,
                HttpMethod.POST,
                new HttpEntity<>(request, authHeaders()),
                placeType
            );
            Long orderId = placeResponse.getBody().data().id();

            // act
            ParameterizedTypeReference<ApiResponse<OrderDetailResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<OrderDetailResponse>> response = testRestTemplate.exchange(
                ENDPOINT_ORDERS + "/" + orderId,
                HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                responseType
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().totalAmount()).isEqualTo(300000L),
                () -> assertThat(response.getBody().data().items()).hasSize(1)
            );
        }

        @DisplayName("존재하지 않는 주문이면, NOT_FOUND 응답을 받는다.")
        @Test
        void returnsNotFound_whenOrderDoesNotExist() {
            // arrange
            createUser();

            // act
            ParameterizedTypeReference<ApiResponse<OrderDetailResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<OrderDetailResponse>> response = testRestTemplate.exchange(
                ENDPOINT_ORDERS + "/999",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("다른 사용자의 주문이면, NOT_FOUND 응답을 받는다.")
        @Test
        void returnsNotFound_whenOrderBelongsToDifferentUser() {
            // arrange
            createUser();
            String otherEncodedPassword = passwordEncoder.encode("Other1234!");
            UserModel otherUser = userJpaRepository.save(
                UserModel.createWithEncodedPassword("otheruser", otherEncodedPassword, "김철수", LocalDate.of(1995, 5, 20), "other@example.com")
            );

            BrandModel brand = brandJpaRepository.save(new BrandModel("나이키", "스포츠 의류 및 신발 브랜드"));
            ProductModel product = createProduct(brand, "에어맥스", 150000L, 10);

            // 다른 사용자로 주문 생성
            PlaceOrderRequest request = new PlaceOrderRequest(
                List.of(new PlaceOrderItemRequest(product.getId(), 1))
            );

            HttpHeaders otherHeaders = new HttpHeaders();
            otherHeaders.set(HEADER_LOGIN_ID, "otheruser");
            otherHeaders.set(HEADER_LOGIN_PW, "Other1234!");

            ParameterizedTypeReference<ApiResponse<OrderDetailResponse>> placeType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<OrderDetailResponse>> placeResponse = testRestTemplate.exchange(
                ENDPOINT_ORDERS,
                HttpMethod.POST,
                new HttpEntity<>(request, otherHeaders),
                placeType
            );
            Long orderId = placeResponse.getBody().data().id();

            // act - testuser로 다른 사용자의 주문 조회
            ParameterizedTypeReference<ApiResponse<OrderDetailResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<OrderDetailResponse>> response = testRestTemplate.exchange(
                ENDPOINT_ORDERS + "/" + orderId,
                HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    record PlaceOrderRequest(
        List<PlaceOrderItemRequest> items,
        Long couponId
    ) {
        PlaceOrderRequest(List<PlaceOrderItemRequest> items) {
            this(items, null);
        }
    }

    record PlaceOrderItemRequest(
        Long productId,
        int quantity
    ) {}

    record OrderDetailResponse(
        Long id,
        Long userId,
        Long usedCouponId,
        Long originalAmount,
        Long discountAmount,
        Long totalAmount,
        String orderedAt,
        List<OrderItemResponse> items
    ) {}

    record UserCouponIssueResponse(
        Long id,
        UserCouponStatus status
    ) {}

    record OrderItemResponse(
        Long id,
        Long productId,
        String productName,
        Long unitPrice,
        int quantity,
        Long lineTotalAmount
    ) {}
}
