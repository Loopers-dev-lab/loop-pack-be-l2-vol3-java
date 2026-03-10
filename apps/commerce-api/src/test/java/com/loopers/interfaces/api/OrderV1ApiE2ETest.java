package com.loopers.interfaces.api;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.domain.product.Product;
import com.loopers.domain.user.User;
import com.loopers.domain.user.UserFixture;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.coupon.CouponJpaRepository;
import com.loopers.infrastructure.coupon.IssuedCouponJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.interfaces.api.order.OrderV1Dto;
import com.loopers.utils.DatabaseCleanUp;
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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderV1ApiE2ETest {

    private static final String ENDPOINT = "/api/v1/orders";
    private static final String RAW_PASSWORD = "TestPass1!";

    private final TestRestTemplate testRestTemplate;
    private final UserJpaRepository userJpaRepository;
    private final BrandJpaRepository brandJpaRepository;
    private final ProductJpaRepository productJpaRepository;
    private final CouponJpaRepository couponJpaRepository;
    private final IssuedCouponJpaRepository issuedCouponJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;
    private final BCryptPasswordEncoder bCryptPasswordEncoder = new BCryptPasswordEncoder();

    @Autowired
    public OrderV1ApiE2ETest(
            TestRestTemplate testRestTemplate,
            UserJpaRepository userJpaRepository,
            BrandJpaRepository brandJpaRepository,
            ProductJpaRepository productJpaRepository,
            CouponJpaRepository couponJpaRepository,
            IssuedCouponJpaRepository issuedCouponJpaRepository,
            DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.userJpaRepository = userJpaRepository;
        this.brandJpaRepository = brandJpaRepository;
        this.productJpaRepository = productJpaRepository;
        this.couponJpaRepository = couponJpaRepository;
        this.issuedCouponJpaRepository = issuedCouponJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    private User savedUser;
    private Product savedProduct;

    @BeforeEach
    void setUp() {
        String encodedPassword = bCryptPasswordEncoder.encode(RAW_PASSWORD);
        savedUser = userJpaRepository.save(
                UserFixture.builder()
                           .loginId("orderTestUser")
                           .password(encodedPassword)
                           .build()
        );

        Brand brand = brandJpaRepository.save(Brand.create("나이키", "스포츠"));
        savedProduct = productJpaRepository.save(Product.create(brand.getId(), "에어맥스", null, 150000, 10));
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders userHeaders(User user, String rawPassword) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", user.getLoginId());
        headers.set("X-Loopers-LoginPw", rawPassword);
        return headers;
    }

    private String ordersUrlWithPeriod(ZonedDateTime startAt, ZonedDateTime endAt) {
        return UriComponentsBuilder.fromPath(ENDPOINT)
                                   .queryParam("startAt", startAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                                   .queryParam("endAt", endAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                                   .toUriString();
    }

    @DisplayName("주문 생성 시")
    @Nested
    class CreateOrder {

        @DisplayName("유효한 요청이면, 201 Created와 주문 정보를 반환한다.")
        @Test
        void returnsCreated_whenValidRequest() {
            // arrange
            OrderV1Dto.CreateRequest request = new OrderV1Dto.CreateRequest(
                    List.of(new OrderV1Dto.OrderItemRequest(savedProduct.getId(), 2)),
                    null
            );
            HttpEntity<OrderV1Dto.CreateRequest> entity = new HttpEntity<>(request, userHeaders(savedUser, RAW_PASSWORD));

            // act
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response =
                    testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});

            // assert
            Product updated = productJpaRepository.findById(savedProduct.getId()).orElseThrow();
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                    () -> assertThat(response.getBody().data().userId()).isEqualTo(savedUser.getId()),
                    () -> assertThat(updated.getStockQuantity()).isEqualTo(8)
            );
        }

        @DisplayName("존재하지 않는 상품이면, 404 Not Found를 반환한다.")
        @Test
        void returnsNotFound_whenProductNotExists() {
            // arrange
            OrderV1Dto.CreateRequest request = new OrderV1Dto.CreateRequest(
                    List.of(new OrderV1Dto.OrderItemRequest(99999L, 1)),
                    null
            );
            HttpEntity<OrderV1Dto.CreateRequest> entity = new HttpEntity<>(request, userHeaders(savedUser, RAW_PASSWORD));

            // act
            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("재고가 부족하면, 400 Bad Request를 반환한다.")
        @Test
        void returnsBadRequest_whenInsufficientStock() {
            // arrange
            OrderV1Dto.CreateRequest request = new OrderV1Dto.CreateRequest(
                    List.of(new OrderV1Dto.OrderItemRequest(savedProduct.getId(), 999)),
                    null
            );
            HttpEntity<OrderV1Dto.CreateRequest> entity = new HttpEntity<>(request, userHeaders(savedUser, RAW_PASSWORD));

            // act
            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("주문 항목이 비어있으면, 400 Bad Request를 반환한다.")
        @Test
        void returnsBadRequest_whenItemsAreEmpty() {
            // arrange
            OrderV1Dto.CreateRequest request = new OrderV1Dto.CreateRequest(List.of(), null);
            HttpEntity<OrderV1Dto.CreateRequest> entity = new HttpEntity<>(request, userHeaders(savedUser, RAW_PASSWORD));

            // act
            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("수량이 0이면, 400 Bad Request를 반환한다.")
        @Test
        void returnsBadRequest_whenQuantityIsZero() {
            // arrange
            OrderV1Dto.CreateRequest request = new OrderV1Dto.CreateRequest(
                    List.of(new OrderV1Dto.OrderItemRequest(savedProduct.getId(), 0)),
                    null
            );
            HttpEntity<OrderV1Dto.CreateRequest> entity = new HttpEntity<>(request, userHeaders(savedUser, RAW_PASSWORD));

            // act
            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("수량이 음수이면, 400 Bad Request를 반환한다.")
        @Test
        void returnsBadRequest_whenQuantityIsNegative() {
            // arrange
            OrderV1Dto.CreateRequest request = new OrderV1Dto.CreateRequest(
                    List.of(new OrderV1Dto.OrderItemRequest(savedProduct.getId(), -1)),
                    null
            );
            HttpEntity<OrderV1Dto.CreateRequest> entity = new HttpEntity<>(request, userHeaders(savedUser, RAW_PASSWORD));

            // act
            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("중복 상품이 포함되면, 400 Bad Request를 반환한다.")
        @Test
        void returnsBadRequest_whenDuplicateProducts() {
            // arrange
            OrderV1Dto.CreateRequest request = new OrderV1Dto.CreateRequest(
                    List.of(
                            new OrderV1Dto.OrderItemRequest(savedProduct.getId(), 1),
                            new OrderV1Dto.OrderItemRequest(savedProduct.getId(), 2)
                    ),
                    null
            );
            HttpEntity<OrderV1Dto.CreateRequest> entity = new HttpEntity<>(request, userHeaders(savedUser, RAW_PASSWORD));

            // act
            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("유효한 쿠폰으로 주문하면, 201 Created와 할인이 적용된 주문 정보를 반환한다.")
        @Test
        void returnsCreated_withDiscountApplied_whenValidCoupon() {
            // arrange
            Coupon coupon = couponJpaRepository.save(
                    Coupon.create("10000원 할인", Coupon.DiscountType.FIXED, 10000L, 10000L, LocalDateTime.now().plusDays(30))
            );
            IssuedCoupon issuedCoupon = issuedCouponJpaRepository.save(
                    IssuedCoupon.create(savedUser.getId(), coupon.getId(), LocalDateTime.now().plusDays(30))
            );

            // 상품 가격: 150000원, 수량: 1 → originalAmount = 150000, discountAmount = 10000, finalAmount = 140000
            OrderV1Dto.CreateRequest request = new OrderV1Dto.CreateRequest(
                    List.of(new OrderV1Dto.OrderItemRequest(savedProduct.getId(), 1)),
                    issuedCoupon.getId()
            );
            HttpEntity<OrderV1Dto.CreateRequest> entity = new HttpEntity<>(request, userHeaders(savedUser, RAW_PASSWORD));

            // act
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response =
                    testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                    () -> assertThat(response.getBody().data().discountAmount()).isEqualTo(10000L),
                    () -> assertThat(response.getBody().data().finalAmount()).isEqualTo(140000L)
            );
        }

        @DisplayName("존재하지 않는 발급 쿠폰으로 주문하면, 404 Not Found를 반환한다.")
        @Test
        void returnsNotFound_whenIssuedCouponNotExists() {
            // arrange
            OrderV1Dto.CreateRequest request = new OrderV1Dto.CreateRequest(
                    List.of(new OrderV1Dto.OrderItemRequest(savedProduct.getId(), 1)),
                    99999L
            );
            HttpEntity<OrderV1Dto.CreateRequest> entity = new HttpEntity<>(request, userHeaders(savedUser, RAW_PASSWORD));

            // act
            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("이미 사용된 쿠폰으로 주문하면, 400 Bad Request를 반환한다.")
        @Test
        void returnsBadRequest_whenCouponAlreadyUsed() {
            // arrange
            Coupon coupon = couponJpaRepository.save(
                    Coupon.create("5000원 할인", Coupon.DiscountType.FIXED, 5000L, 5000L, LocalDateTime.now().plusDays(30))
            );
            IssuedCoupon issuedCoupon = IssuedCoupon.create(savedUser.getId(), coupon.getId(), LocalDateTime.now().plusDays(30));
            issuedCoupon.markAsUsed();
            issuedCoupon = issuedCouponJpaRepository.save(issuedCoupon);

            OrderV1Dto.CreateRequest request = new OrderV1Dto.CreateRequest(
                    List.of(new OrderV1Dto.OrderItemRequest(savedProduct.getId(), 1)),
                    issuedCoupon.getId()
            );
            HttpEntity<OrderV1Dto.CreateRequest> entity = new HttpEntity<>(request, userHeaders(savedUser, RAW_PASSWORD));

            // act
            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("최소 주문 금액 미충족 쿠폰으로 주문하면, 400 Bad Request를 반환한다.")
        @Test
        void returnsBadRequest_whenMinOrderAmountNotMet() {
            // arrange
            Coupon coupon = couponJpaRepository.save(
                    Coupon.create("고액 할인", Coupon.DiscountType.FIXED, 5000L, 500000L, LocalDateTime.now().plusDays(30))
            );
            IssuedCoupon issuedCoupon = issuedCouponJpaRepository.save(
                    IssuedCoupon.create(savedUser.getId(), coupon.getId(), LocalDateTime.now().plusDays(30))
            );

            // 상품 가격: 150000원 < 최소 주문 금액: 500000원
            OrderV1Dto.CreateRequest request = new OrderV1Dto.CreateRequest(
                    List.of(new OrderV1Dto.OrderItemRequest(savedProduct.getId(), 1)),
                    issuedCoupon.getId()
            );
            HttpEntity<OrderV1Dto.CreateRequest> entity = new HttpEntity<>(request, userHeaders(savedUser, RAW_PASSWORD));

            // act
            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("주문 목록 조회 시")
    @Nested
    class GetOrders {

        @DisplayName("주문이 있으면, 200 OK와 주문 목록을 반환한다.")
        @Test
        void returnsOk_withOrderList() {
            // arrange
            OrderV1Dto.CreateRequest request = new OrderV1Dto.CreateRequest(
                    List.of(new OrderV1Dto.OrderItemRequest(savedProduct.getId(), 1)),
                    null
            );
            HttpEntity<OrderV1Dto.CreateRequest> createEntity = new HttpEntity<>(request, userHeaders(savedUser, RAW_PASSWORD));
            testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, createEntity, new ParameterizedTypeReference<>() {});

            String url = ordersUrlWithPeriod(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(1));
            HttpEntity<Void> getEntity = new HttpEntity<>(userHeaders(savedUser, RAW_PASSWORD));

            // act
            ResponseEntity<ApiResponse<List<OrderV1Dto.OrderResponse>>> response =
                    testRestTemplate.exchange(url, HttpMethod.GET, getEntity, new ParameterizedTypeReference<>() {});

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data()).isNotEmpty()
            );
        }

        @DisplayName("주문이 없으면, 200 OK와 빈 목록을 반환한다.")
        @Test
        void returnsEmptyList_whenNoOrders() {
            // arrange
            String url = ordersUrlWithPeriod(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(1));
            HttpEntity<Void> getEntity = new HttpEntity<>(userHeaders(savedUser, RAW_PASSWORD));

            // act
            ResponseEntity<ApiResponse<List<OrderV1Dto.OrderResponse>>> response =
                    testRestTemplate.exchange(url, HttpMethod.GET, getEntity, new ParameterizedTypeReference<>() {});

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data()).isEmpty()
            );
        }
    }

    @DisplayName("주문 상세 조회 시")
    @Nested
    class GetOrder {

        @DisplayName("주문이 존재하면, 200 OK와 주문 상세를 반환한다.")
        @Test
        void returnsOk_whenOrderExists() {
            // arrange
            OrderV1Dto.CreateRequest request = new OrderV1Dto.CreateRequest(
                    List.of(new OrderV1Dto.OrderItemRequest(savedProduct.getId(), 2)),
                    null
            );
            HttpEntity<OrderV1Dto.CreateRequest> createEntity = new HttpEntity<>(request, userHeaders(savedUser, RAW_PASSWORD));
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> created =
                    testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, createEntity, new ParameterizedTypeReference<>() {});

            Long orderId = created.getBody().data().id();
            HttpEntity<Void> getEntity = new HttpEntity<>(userHeaders(savedUser, RAW_PASSWORD));

            // act
            ResponseEntity<ApiResponse<OrderV1Dto.OrderDetailResponse>> response =
                    testRestTemplate.exchange(ENDPOINT + "/" + orderId, HttpMethod.GET, getEntity, new ParameterizedTypeReference<>() {});

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().items()).isNotEmpty(),
                    () -> assertThat(response.getBody().data().items().get(0).productId()).isEqualTo(savedProduct.getId())
            );
        }

        @DisplayName("타인의 주문을 조회하면, 404 Not Found를 반환한다.")
        @Test
        void returnsNotFound_whenOrderNotOwned() {
            // arrange
            OrderV1Dto.CreateRequest request = new OrderV1Dto.CreateRequest(
                    List.of(new OrderV1Dto.OrderItemRequest(savedProduct.getId(), 1)),
                    null
            );
            HttpEntity<OrderV1Dto.CreateRequest> createEntity = new HttpEntity<>(request, userHeaders(savedUser, RAW_PASSWORD));
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> created =
                    testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, createEntity, new ParameterizedTypeReference<>() {});

            User otherUser = userJpaRepository.save(
                    UserFixture.builder()
                               .loginId("orderOtherUser")
                               .password(bCryptPasswordEncoder.encode("OtherPass1!"))
                               .build()
            );

            Long orderId = created.getBody().data().id();
            HttpEntity<Void> getEntity = new HttpEntity<>(userHeaders(otherUser, "OtherPass1!"));

            // act
            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(ENDPOINT + "/" + orderId, HttpMethod.GET, getEntity, new ParameterizedTypeReference<>() {});

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
