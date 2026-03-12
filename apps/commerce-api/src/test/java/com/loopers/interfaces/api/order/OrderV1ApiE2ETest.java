package com.loopers.interfaces.api.order;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.product.Money;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.Quantity;
import com.loopers.domain.product.Stock;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.order.OrderJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.user.UserV1Dto;
import com.loopers.support.error.ErrorType;
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
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderV1ApiE2ETest {

    private static final String VALID_LOGIN_ID = "orderuser1";
    private static final String VALID_PASSWORD = "order@1234";
    private static final String OTHER_LOGIN_ID = "otheruser1";
    private static final String OTHER_PASSWORD = "other@1234";
    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";

    private static final String SIGNUP_ENDPOINT = "/api/v1/users/signup";
    private static final String ENDPOINT_CREATE_ORDER = "/api/v1/orders";
    private static final String ENDPOINT_GET_ORDERS = "/api/v1/orders";
    private static final Function<Long, String> ENDPOINT_GET_ORDER = id -> "/api/v1/orders/" + id;

    private static final String VALID_PRODUCT_NAME = "나이키 에어맥스";
    private static final int VALID_PRICE = 10000;
    private static final int VALID_STOCK = 10;
    private static final Long NOT_EXISTED_ORDER_ID = 999L;
    private static final Long NOT_EXISTED_PRODUCT_ID = 999L;

    private final TestRestTemplate testRestTemplate;
    private final DatabaseCleanUp databaseCleanUp;
    private final BrandJpaRepository brandJpaRepository;
    private final ProductJpaRepository productJpaRepository;
    private final OrderJpaRepository orderJpaRepository;
    private final UserJpaRepository userJpaRepository;

    @Autowired
    public OrderV1ApiE2ETest(
            TestRestTemplate testRestTemplate,
            DatabaseCleanUp databaseCleanUp,
            BrandJpaRepository brandJpaRepository,
            ProductJpaRepository productJpaRepository,
            OrderJpaRepository orderJpaRepository,
            UserJpaRepository userJpaRepository
    ) {
        this.testRestTemplate = testRestTemplate;
        this.databaseCleanUp = databaseCleanUp;
        this.brandJpaRepository = brandJpaRepository;
        this.productJpaRepository = productJpaRepository;
        this.orderJpaRepository = orderJpaRepository;
        this.userJpaRepository = userJpaRepository;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    Long signUpAndGetUserId(String loginId, String password, String name) {
        UserV1Dto.SignupRequest signupRequest = new UserV1Dto.SignupRequest(
                loginId, password, name, "1990-01-01", loginId + "@test.com"
        );
        testRestTemplate.exchange(
                SIGNUP_ENDPOINT, HttpMethod.POST,
                new HttpEntity<>(signupRequest),
                new ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>>() {}
        );
        return userJpaRepository.findByLoginId(loginId).orElseThrow().getId();
    }

    HttpHeaders createUserHeaders(String loginId, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_LOGIN_ID, loginId);
        headers.set(HEADER_LOGIN_PW, password);
        return headers;
    }

    HttpHeaders createUserHeaders() {
        return createUserHeaders(VALID_LOGIN_ID, VALID_PASSWORD);
    }

    @DisplayName("POST /api/v1/orders")
    @Nested
    class CreateOrder {

        @DisplayName("인증된 회원이 존재하는 상품으로 주문하면, 200 OK와 주문 정보를 반환한다.")
        @Test
        void returnsOrderResponse_whenOrderCreatedSuccessfully() {
            // arrange
            signUpAndGetUserId(VALID_LOGIN_ID, VALID_PASSWORD, "주문유저");
            Brand brand = brandJpaRepository.save(new Brand("나이키"));
            Product product = productJpaRepository.save(
                    new Product(brand.getId(), VALID_PRODUCT_NAME, new Money(VALID_PRICE), new Stock(VALID_STOCK)));
            OrderV1Dto.OrderCreateRequest request = new OrderV1Dto.OrderCreateRequest(
                    List.of(new OrderV1Dto.OrderItemRequest(product.getId(), 2)), null
            );

            // act
            ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>> responseType =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = testRestTemplate.exchange(
                    ENDPOINT_CREATE_ORDER, HttpMethod.POST,
                    new HttpEntity<>(request, createUserHeaders()),
                    responseType
            );

            // assert
            assertAll(
                    () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                    () -> assertThat(response.getBody().data().orderId()).isPositive(),
                    () -> assertThat(response.getBody().data().items()).hasSize(1),
                    () -> assertThat(response.getBody().data().items().get(0).productName()).isEqualTo(VALID_PRODUCT_NAME)
            );
        }

        @DisplayName("인증 헤더 없이 주문하면, 401 UNAUTHORIZED를 반환한다.")
        @Test
        void returnsUnauthorized_whenAuthHeaderMissing() {
            // arrange
            Brand brand = brandJpaRepository.save(new Brand("나이키"));
            Product product = productJpaRepository.save(
                    new Product(brand.getId(), VALID_PRODUCT_NAME, new Money(VALID_PRICE), new Stock(VALID_STOCK)));
            OrderV1Dto.OrderCreateRequest request = new OrderV1Dto.OrderCreateRequest(
                    List.of(new OrderV1Dto.OrderItemRequest(product.getId(), 1)), null
            );

            // act
            ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>> responseType =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = testRestTemplate.exchange(
                    ENDPOINT_CREATE_ORDER, HttpMethod.POST,
                    new HttpEntity<>(request),
                    responseType
            );

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(ErrorType.UNAUTHORIZED.getStatus()),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL)
            );
        }

        @DisplayName("존재하지 않는 상품으로 주문하면, 404 NOT_FOUND를 반환한다.")
        @Test
        void returnsNotFound_whenProductNotExist() {
            // arrange
            signUpAndGetUserId(VALID_LOGIN_ID, VALID_PASSWORD, "주문유저");
            OrderV1Dto.OrderCreateRequest request = new OrderV1Dto.OrderCreateRequest(
                    List.of(new OrderV1Dto.OrderItemRequest(NOT_EXISTED_PRODUCT_ID, 1)), null
            );

            // act
            ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>> responseType =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = testRestTemplate.exchange(
                    ENDPOINT_CREATE_ORDER, HttpMethod.POST,
                    new HttpEntity<>(request, createUserHeaders()),
                    responseType
            );

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(ErrorType.NOT_FOUND.getStatus()),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL)
            );
        }

        @DisplayName("재고가 부족한 상품으로 주문하면, 400 BAD_REQUEST를 반환한다.")
        @Test
        void returnsBadRequest_whenStockIsInsufficient() {
            // arrange
            signUpAndGetUserId(VALID_LOGIN_ID, VALID_PASSWORD, "주문유저");
            Brand brand = brandJpaRepository.save(new Brand("나이키"));
            Product product = productJpaRepository.save(
                    new Product(brand.getId(), VALID_PRODUCT_NAME, new Money(VALID_PRICE), new Stock(1)));
            OrderV1Dto.OrderCreateRequest request = new OrderV1Dto.OrderCreateRequest(
                    List.of(new OrderV1Dto.OrderItemRequest(product.getId(), 5)), null // 재고(1) < 주문(5)
            );

            // act
            ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>> responseType =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = testRestTemplate.exchange(
                    ENDPOINT_CREATE_ORDER, HttpMethod.POST,
                    new HttpEntity<>(request, createUserHeaders()),
                    responseType
            );

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(ErrorType.BAD_REQUEST.getStatus()),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL)
            );
        }

        @DisplayName("주문 후 상품 재고가 주문 수량만큼 차감된다.")
        @Test
        void decreasesStock_afterOrderCreated() {
            // arrange
            signUpAndGetUserId(VALID_LOGIN_ID, VALID_PASSWORD, "주문유저");
            Brand brand = brandJpaRepository.save(new Brand("나이키"));
            Product product = productJpaRepository.save(
                    new Product(brand.getId(), VALID_PRODUCT_NAME, new Money(VALID_PRICE), new Stock(VALID_STOCK)));
            int orderQuantity = 3;
            OrderV1Dto.OrderCreateRequest request = new OrderV1Dto.OrderCreateRequest(
                    List.of(new OrderV1Dto.OrderItemRequest(product.getId(), orderQuantity)), null
            );

            // act
            testRestTemplate.exchange(
                    ENDPOINT_CREATE_ORDER, HttpMethod.POST,
                    new HttpEntity<>(request, createUserHeaders()),
                    new ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>>() {}
            );

            // assert
            Product updated = productJpaRepository.findById(product.getId()).orElseThrow();
            assertThat(updated.getStock().getQuantity()).isEqualTo(VALID_STOCK - orderQuantity);
        }
    }

    @DisplayName("GET /api/v1/orders")
    @Nested
    class GetOrders {

        @DisplayName("기간을 지정하여 자신의 주문 목록을 조회하면, 200 OK와 주문 목록을 반환한다.")
        @Test
        void returnsOrderList_whenUserHasOrders() {
            // arrange
            Long userId = signUpAndGetUserId(VALID_LOGIN_ID, VALID_PASSWORD, "주문유저");
            Brand brand = brandJpaRepository.save(new Brand("나이키"));
            Product product = productJpaRepository.save(
                    new Product(brand.getId(), VALID_PRODUCT_NAME, new Money(VALID_PRICE), new Stock(VALID_STOCK)));
            orderJpaRepository.save(new Order(userId,
                    List.of(new OrderItem(product.getId(), new Quantity(1), VALID_PRODUCT_NAME, brand.getName(), product.getPrice())),
                    null, product.getPrice(), new Money(0)));

            // act
            ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderListResponse>> responseType =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<OrderV1Dto.OrderListResponse>> response = testRestTemplate.exchange(
                    ENDPOINT_GET_ORDERS + "?startAt=2020-01-01&endAt=2099-12-31",
                    HttpMethod.GET,
                    new HttpEntity<>(createUserHeaders()),
                    responseType
            );

            // assert
            assertAll(
                    () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                    () -> assertThat(response.getBody().data().orders()).hasSize(1)
            );
        }

        @DisplayName("startAt 파라미터 없이 조회하면, 400 BAD_REQUEST를 반환한다.")
        @Test
        void returnsBadRequest_whenStartAtIsMissing() {
            // arrange
            signUpAndGetUserId(VALID_LOGIN_ID, VALID_PASSWORD, "주문유저");

            // act
            ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderListResponse>> responseType =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<OrderV1Dto.OrderListResponse>> response = testRestTemplate.exchange(
                    ENDPOINT_GET_ORDERS + "?endAt=2099-12-31",
                    HttpMethod.GET,
                    new HttpEntity<>(createUserHeaders()),
                    responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(ErrorType.BAD_REQUEST.getStatus());
        }

        @DisplayName("endAt 파라미터 없이 조회하면, 400 BAD_REQUEST를 반환한다.")
        @Test
        void returnsBadRequest_whenEndAtIsMissing() {
            // arrange
            signUpAndGetUserId(VALID_LOGIN_ID, VALID_PASSWORD, "주문유저");

            // act
            ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderListResponse>> responseType =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<OrderV1Dto.OrderListResponse>> response = testRestTemplate.exchange(
                    ENDPOINT_GET_ORDERS + "?startAt=2020-01-01",
                    HttpMethod.GET,
                    new HttpEntity<>(createUserHeaders()),
                    responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(ErrorType.BAD_REQUEST.getStatus());
        }

    }

    @DisplayName("GET /api/v1/orders/{orderId}")
    @Nested
    class GetOrder {

        @DisplayName("자신의 주문을 상세 조회하면, 200 OK와 주문 상세 정보를 반환한다.")
        @Test
        void returnsOrderDetail_whenOrderBelongsToUser() {
            // arrange
            Long userId = signUpAndGetUserId(VALID_LOGIN_ID, VALID_PASSWORD, "주문유저");
            Brand brand = brandJpaRepository.save(new Brand("나이키"));
            Product product = productJpaRepository.save(
                    new Product(brand.getId(), VALID_PRODUCT_NAME, new Money(VALID_PRICE), new Stock(VALID_STOCK)));
            Order order = orderJpaRepository.save(new Order(userId,
                    List.of(new OrderItem(product.getId(), new Quantity(1), VALID_PRODUCT_NAME, brand.getName(), product.getPrice())),
                    null, product.getPrice(), new Money(0)));

            // act
            ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>> responseType =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = testRestTemplate.exchange(
                    ENDPOINT_GET_ORDER.apply(order.getId()),
                    HttpMethod.GET,
                    new HttpEntity<>(createUserHeaders()),
                    responseType
            );

            // assert
            assertAll(
                    () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                    () -> assertThat(response.getBody().data().orderId()).isEqualTo(order.getId()),
                    () -> assertThat(response.getBody().data().items()).hasSize(1)
            );
        }

        @DisplayName("타인의 주문을 조회하면, 404 NOT_FOUND를 반환한다. (BR-O06)")
        @Test
        void returnsNotFound_whenOrderBelongsToOtherUser() {
            // arrange
            signUpAndGetUserId(VALID_LOGIN_ID, VALID_PASSWORD, "주문유저");
            Long otherUserId = signUpAndGetUserId(OTHER_LOGIN_ID, OTHER_PASSWORD, "타인유저");
            Brand brand = brandJpaRepository.save(new Brand("나이키"));
            Product product = productJpaRepository.save(
                    new Product(brand.getId(), VALID_PRODUCT_NAME, new Money(VALID_PRICE), new Stock(VALID_STOCK)));
            Order otherOrder = orderJpaRepository.save(new Order(otherUserId,
                    List.of(new OrderItem(product.getId(), new Quantity(1), VALID_PRODUCT_NAME, brand.getName(), product.getPrice())),
                    null, product.getPrice(), new Money(0)));

            // act
            ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>> responseType =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = testRestTemplate.exchange(
                    ENDPOINT_GET_ORDER.apply(otherOrder.getId()),
                    HttpMethod.GET,
                    new HttpEntity<>(createUserHeaders()), // VALID_LOGIN_ID로 접근
                    responseType
            );

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(ErrorType.NOT_FOUND.getStatus()),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL)
            );
        }

        @DisplayName("존재하지 않는 주문을 조회하면, 404 NOT_FOUND를 반환한다.")
        @Test
        void returnsNotFound_whenOrderDoesNotExist() {
            // arrange
            signUpAndGetUserId(VALID_LOGIN_ID, VALID_PASSWORD, "주문유저");

            // act
            ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>> responseType =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = testRestTemplate.exchange(
                    ENDPOINT_GET_ORDER.apply(NOT_EXISTED_ORDER_ID),
                    HttpMethod.GET,
                    new HttpEntity<>(createUserHeaders()),
                    responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(ErrorType.NOT_FOUND.getStatus());
        }

    }
}
