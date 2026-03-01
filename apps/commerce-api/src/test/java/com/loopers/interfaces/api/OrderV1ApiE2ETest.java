package com.loopers.interfaces.api;

import com.loopers.interfaces.api.brand.AdminBrandV1Dto;
import com.loopers.interfaces.api.cart.CartV1Dto;
import com.loopers.interfaces.api.order.OrderV1Dto;
import com.loopers.interfaces.api.product.AdminProductV1Dto;
import com.loopers.interfaces.api.user.UserV1Dto;
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

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderV1ApiE2ETest {

    private static final String ORDER_ENDPOINT = "/api/v1/orders";
    private static final String CART_ITEMS_ENDPOINT = "/api/v1/cart/items";
    private static final String ADMIN_BRAND_ENDPOINT = "/api-admin/v1/brands";
    private static final String ADMIN_PRODUCT_ENDPOINT = "/api-admin/v1/products";
    private static final String SIGNUP_ENDPOINT = "/api/v1/users";

    private final TestRestTemplate testRestTemplate;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public OrderV1ApiE2ETest(TestRestTemplate testRestTemplate, DatabaseCleanUp databaseCleanUp) {
        this.testRestTemplate = testRestTemplate;
        this.databaseCleanUp = databaseCleanUp;
    }

    private Long productId;
    private Long productId2;

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-Ldap", "loopers.admin");
        headers.set("Content-Type", "application/json");
        return headers;
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", "testUser1");
        headers.set("X-Loopers-LoginPw", "Abcd1234!");
        headers.set("Content-Type", "application/json");
        return headers;
    }

    private void signupUser() {
        UserV1Dto.SignupRequest request = new UserV1Dto.SignupRequest(
            "testUser1", "Abcd1234!", "홍길동", LocalDate.of(1995, 3, 15), "test@example.com"
        );
        testRestTemplate.exchange(SIGNUP_ENDPOINT, HttpMethod.POST, new HttpEntity<>(request),
            new ParameterizedTypeReference<ApiResponse<UserV1Dto.SignupResponse>>() {});
    }

    @BeforeEach
    void setUp() {
        signupUser();

        AdminBrandV1Dto.CreateRequest brandRequest = new AdminBrandV1Dto.CreateRequest("나이키");
        ResponseEntity<ApiResponse<AdminBrandV1Dto.BrandResponse>> brandResponse = testRestTemplate.exchange(
            ADMIN_BRAND_ENDPOINT, HttpMethod.POST, new HttpEntity<>(brandRequest, adminHeaders()),
            new ParameterizedTypeReference<>() {}
        );
        Long brandId = brandResponse.getBody().data().id();

        AdminProductV1Dto.CreateRequest productRequest1 = new AdminProductV1Dto.CreateRequest(brandId, "에어맥스", 129000, 100);
        ResponseEntity<ApiResponse<AdminProductV1Dto.ProductResponse>> productResponse1 = testRestTemplate.exchange(
            ADMIN_PRODUCT_ENDPOINT, HttpMethod.POST, new HttpEntity<>(productRequest1, adminHeaders()),
            new ParameterizedTypeReference<>() {}
        );
        productId = productResponse1.getBody().data().id();

        AdminProductV1Dto.CreateRequest productRequest2 = new AdminProductV1Dto.CreateRequest(brandId, "에어포스1", 109000, 200);
        ResponseEntity<ApiResponse<AdminProductV1Dto.ProductResponse>> productResponse2 = testRestTemplate.exchange(
            ADMIN_PRODUCT_ENDPOINT, HttpMethod.POST, new HttpEntity<>(productRequest2, adminHeaders()),
            new ParameterizedTypeReference<>() {}
        );
        productId2 = productResponse2.getBody().data().id();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private OrderV1Dto.OrderDetailResponse createOrder(Long pId, int quantity) {
        OrderV1Dto.CreateOrderRequest request = new OrderV1Dto.CreateOrderRequest(
            List.of(new OrderV1Dto.OrderItemRequest(pId, quantity)), null
        );
        ResponseEntity<ApiResponse<OrderV1Dto.OrderDetailResponse>> response = testRestTemplate.exchange(
            ORDER_ENDPOINT, HttpMethod.POST, new HttpEntity<>(request, authHeaders()),
            new ParameterizedTypeReference<>() {}
        );
        return response.getBody().data();
    }

    @DisplayName("POST /api/v1/orders")
    @Nested
    class CreateOrder {

        @DisplayName("올바른 주문 요청이면, 주문 상세 정보를 반환한다.")
        @Test
        void returnsOrderDetail_whenValidRequest() {
            OrderV1Dto.CreateOrderRequest request = new OrderV1Dto.CreateOrderRequest(
                List.of(
                    new OrderV1Dto.OrderItemRequest(productId, 2),
                    new OrderV1Dto.OrderItemRequest(productId2, 1)
                ), null
            );

            ResponseEntity<ApiResponse<OrderV1Dto.OrderDetailResponse>> response = testRestTemplate.exchange(
                ORDER_ENDPOINT, HttpMethod.POST, new HttpEntity<>(request, authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().orderId()).isNotNull(),
                () -> assertThat(response.getBody().data().totalPrice()).isEqualTo(129000 * 2 + 109000),
                () -> assertThat(response.getBody().data().status()).isEqualTo("ORDERED"),
                () -> assertThat(response.getBody().data().items()).hasSize(2)
            );
        }

        @DisplayName("주문 후 상품 재고가 차감된다.")
        @Test
        void deductsStock_whenOrderSucceeds() {
            createOrder(productId, 3);

            ResponseEntity<ApiResponse<AdminProductV1Dto.ProductResponse>> productResponse = testRestTemplate.exchange(
                ADMIN_PRODUCT_ENDPOINT + "/" + productId, HttpMethod.GET, new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(productResponse.getBody().data().stock()).isEqualTo(97);
        }

        @DisplayName("중복된 상품이 포함되면, 400 BAD_REQUEST를 반환한다.")
        @Test
        void returnsBadRequest_whenDuplicateProducts() {
            OrderV1Dto.CreateOrderRequest request = new OrderV1Dto.CreateOrderRequest(
                List.of(
                    new OrderV1Dto.OrderItemRequest(productId, 2),
                    new OrderV1Dto.OrderItemRequest(productId, 3)
                ), null
            );

            ResponseEntity<ApiResponse<OrderV1Dto.OrderDetailResponse>> response = testRestTemplate.exchange(
                ORDER_ENDPOINT, HttpMethod.POST, new HttpEntity<>(request, authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("재고가 부족하면, 400 BAD_REQUEST를 반환한다.")
        @Test
        void returnsBadRequest_whenStockInsufficient() {
            OrderV1Dto.CreateOrderRequest request = new OrderV1Dto.CreateOrderRequest(
                List.of(new OrderV1Dto.OrderItemRequest(productId, 999)), null
            );

            ResponseEntity<ApiResponse<OrderV1Dto.OrderDetailResponse>> response = testRestTemplate.exchange(
                ORDER_ENDPOINT, HttpMethod.POST, new HttpEntity<>(request, authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("존재하지 않는 상품이면, 404 NOT_FOUND를 반환한다.")
        @Test
        void returnsNotFound_whenProductDoesNotExist() {
            OrderV1Dto.CreateOrderRequest request = new OrderV1Dto.CreateOrderRequest(
                List.of(new OrderV1Dto.OrderItemRequest(999L, 1)), null
            );

            ResponseEntity<ApiResponse<OrderV1Dto.OrderDetailResponse>> response = testRestTemplate.exchange(
                ORDER_ENDPOINT, HttpMethod.POST, new HttpEntity<>(request, authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("인증되지 않은 사용자이면, 401 UNAUTHORIZED를 반환한다.")
        @Test
        void returnsUnauthorized_whenNotAuthenticated() {
            OrderV1Dto.CreateOrderRequest request = new OrderV1Dto.CreateOrderRequest(
                List.of(new OrderV1Dto.OrderItemRequest(productId, 1)), null
            );

            ResponseEntity<ApiResponse<OrderV1Dto.OrderDetailResponse>> response = testRestTemplate.exchange(
                ORDER_ENDPOINT, HttpMethod.POST, new HttpEntity<>(request),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("POST /api/v1/orders/cart")
    @Nested
    class CreateOrderFromCart {

        @DisplayName("장바구니에 항목이 있으면, 주문이 생성되고 장바구니가 비워진다.")
        @Test
        void createsOrderAndClearsCart_whenCartHasItems() {
            // Add items to cart
            CartV1Dto.AddRequest cartRequest1 = new CartV1Dto.AddRequest(productId, 2);
            testRestTemplate.exchange(
                CART_ITEMS_ENDPOINT, HttpMethod.POST, new HttpEntity<>(cartRequest1, authHeaders()),
                new ParameterizedTypeReference<ApiResponse<Object>>() {}
            );
            CartV1Dto.AddRequest cartRequest2 = new CartV1Dto.AddRequest(productId2, 1);
            testRestTemplate.exchange(
                CART_ITEMS_ENDPOINT, HttpMethod.POST, new HttpEntity<>(cartRequest2, authHeaders()),
                new ParameterizedTypeReference<ApiResponse<Object>>() {}
            );

            // Create order from cart
            ResponseEntity<ApiResponse<OrderV1Dto.OrderDetailResponse>> response = testRestTemplate.exchange(
                ORDER_ENDPOINT + "/cart", HttpMethod.POST, new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().totalPrice()).isEqualTo(129000 * 2 + 109000),
                () -> assertThat(response.getBody().data().items()).hasSize(2)
            );

            // Verify cart is empty
            ResponseEntity<ApiResponse<CartV1Dto.CartResponse>> cartResponse = testRestTemplate.exchange(
                "/api/v1/cart", HttpMethod.GET, new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {}
            );
            assertThat(cartResponse.getBody().data().items()).isEmpty();
        }

        @DisplayName("장바구니가 비어있으면, 400 BAD_REQUEST를 반환한다.")
        @Test
        void returnsBadRequest_whenCartIsEmpty() {
            ResponseEntity<ApiResponse<OrderV1Dto.OrderDetailResponse>> response = testRestTemplate.exchange(
                ORDER_ENDPOINT + "/cart", HttpMethod.POST, new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("GET /api/v1/orders")
    @Nested
    class GetMyOrders {

        @DisplayName("기간 내 주문이 있으면, 주문 목록을 반환한다.")
        @Test
        void returnsOrders_whenOrdersExistInRange() {
            createOrder(productId, 2);
            createOrder(productId2, 1);

            String today = LocalDate.now().toString();
            String tomorrow = LocalDate.now().plusDays(1).toString();

            ResponseEntity<ApiResponse<OrderV1Dto.OrderPageResponse>> response = testRestTemplate.exchange(
                ORDER_ENDPOINT + "?startAt=" + today + "&endAt=" + tomorrow,
                HttpMethod.GET, new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().content()).hasSize(2),
                () -> assertThat(response.getBody().data().totalElements()).isEqualTo(2)
            );
        }

        @DisplayName("기간 외 주문이면, 빈 목록을 반환한다.")
        @Test
        void returnsEmpty_whenNoOrdersInRange() {
            createOrder(productId, 2);

            String futureStart = LocalDate.now().plusDays(10).toString();
            String futureEnd = LocalDate.now().plusDays(20).toString();

            ResponseEntity<ApiResponse<OrderV1Dto.OrderPageResponse>> response = testRestTemplate.exchange(
                ORDER_ENDPOINT + "?startAt=" + futureStart + "&endAt=" + futureEnd,
                HttpMethod.GET, new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().content()).isEmpty()
            );
        }
    }

    @DisplayName("GET /api/v1/orders/{orderId}")
    @Nested
    class GetMyOrderDetail {

        @DisplayName("본인의 주문이면, 주문 상세를 반환한다.")
        @Test
        void returnsOrderDetail_whenOwner() {
            OrderV1Dto.OrderDetailResponse created = createOrder(productId, 2);

            ResponseEntity<ApiResponse<OrderV1Dto.OrderDetailResponse>> response = testRestTemplate.exchange(
                ORDER_ENDPOINT + "/" + created.orderId(), HttpMethod.GET, new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().orderId()).isEqualTo(created.orderId()),
                () -> assertThat(response.getBody().data().items()).hasSize(1),
                () -> assertThat(response.getBody().data().items().get(0).productName()).isEqualTo("에어맥스"),
                () -> assertThat(response.getBody().data().items().get(0).brandName()).isEqualTo("나이키")
            );
        }

        @DisplayName("존재하지 않는 주문이면, 404 NOT_FOUND를 반환한다.")
        @Test
        void returnsNotFound_whenOrderDoesNotExist() {
            ResponseEntity<ApiResponse<OrderV1Dto.OrderDetailResponse>> response = testRestTemplate.exchange(
                ORDER_ENDPOINT + "/999", HttpMethod.GET, new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
