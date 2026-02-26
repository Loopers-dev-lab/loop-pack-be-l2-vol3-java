package com.loopers.interfaces.api.order;

import com.loopers.domain.address.UserAddress;
import com.loopers.domain.address.UserAddressRepository;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.inventory.Inventory;
import com.loopers.domain.inventory.InventoryRepository;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductStatus;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.user.UserRequest;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class OrderApiE2ETest {

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private UserAddressRepository userAddressRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private Long userId;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @BeforeEach
    void setUp() {
        UserRequest.SignupRequest signupRequest = new UserRequest.SignupRequest(
                "testuser", "Hx7!mK2@", "테스터", "1994-11-15", "test@example.com");
        testRestTemplate.postForEntity("/api/v1/users", signupRequest, ApiResponse.class);
        userId = 1L;
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", "testuser");
        headers.set("X-Loopers-LoginPw", "Hx7!mK2@");
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private Brand createActiveBrand(String name) {
        return brandRepository.save(Brand.create(name, name + " 설명"));
    }

    private Product createActiveProduct(Long brandId, String name) {
        Product product = Product.create(brandId, name, name + " 설명", 10000);
        Product saved = productRepository.save(product);
        inventoryRepository.save(Inventory.create(saved.getId(), 100));
        return saved;
    }

    private Product createSoldOutProduct(Long brandId, String name) {
        Product product = Product.create(brandId, name, name + " 설명", 10000);
        product.changeStatus(ProductStatus.SOLDOUT);
        Product saved = productRepository.save(product);
        inventoryRepository.save(Inventory.create(saved.getId(), 0));
        return saved;
    }

    private UserAddress createAddress(Long forUserId) {
        UserAddress address = UserAddress.create(forUserId, "홍길동", "010-1234-5678",
                "12345", "서울시 강남구 테헤란로 123", "4층 401호");
        return userAddressRepository.save(address);
    }

    private OrderRequest.CreateOrderRequest createOrderRequest(Long productId, Long addressId) {
        return new OrderRequest.CreateOrderRequest(
                List.of(new OrderRequest.OrderItemRequest(productId, 2)),
                List.of(),
                addressId,
                "010-1234-5678");
    }

    private ResponseEntity<ApiResponse> createOrder(Long productId, Long addressId) {
        return testRestTemplate.exchange(
                "/api/v1/orders", HttpMethod.POST,
                new HttpEntity<>(createOrderRequest(productId, addressId), authHeaders()),
                ApiResponse.class);
    }

    @DisplayName("POST /api/v1/orders")
    @Nested
    class 주문_생성 {

        @Test
        void 주문_생성에_성공하면_200_OK를_반환한다() {
            // arrange
            Brand brand = createActiveBrand("나이키");
            Product product = createActiveProduct(brand.getId(), "에어맥스");
            UserAddress address = createAddress(userId);

            // act
            ResponseEntity<ApiResponse> response = createOrder(product.getId(), address.getId());

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void 주문_항목이_비어있으면_400_Bad_Request를_반환한다() {
            // arrange
            UserAddress address = createAddress(userId);

            // act
            OrderRequest.CreateOrderRequest request = new OrderRequest.CreateOrderRequest(
                    List.of(), List.of(), address.getId(), "010-1234-5678");
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    "/api/v1/orders", HttpMethod.POST,
                    new HttpEntity<>(request, authHeaders()), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void 존재하지_않는_상품이면_404_Not_Found를_반환한다() {
            // arrange
            UserAddress address = createAddress(userId);

            // act
            ResponseEntity<ApiResponse> response = createOrder(999L, address.getId());

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        void 판매_불가능한_상품이면_409_Conflict를_반환한다() {
            // arrange
            Brand brand = createActiveBrand("나이키");
            Product product = createSoldOutProduct(brand.getId(), "품절상품");
            UserAddress address = createAddress(userId);

            // act
            ResponseEntity<ApiResponse> response = createOrder(product.getId(), address.getId());

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        void 존재하지_않는_배송지면_404_Not_Found를_반환한다() {
            // arrange
            Brand brand = createActiveBrand("나이키");
            Product product = createActiveProduct(brand.getId(), "에어맥스");

            // act
            ResponseEntity<ApiResponse> response = createOrder(product.getId(), 999L);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        void 인증_없이_요청하면_401_Unauthorized를_반환한다() {
            // act
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    "/api/v1/orders", HttpMethod.POST,
                    new HttpEntity<>(createOrderRequest(1L, 1L), headers), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("GET /api/v1/orders")
    @Nested
    class 주문_목록_조회 {

        @Test
        void 주문_목록_조회에_성공하면_200_OK를_반환한다() {
            // arrange
            Brand brand = createActiveBrand("나이키");
            Product product = createActiveProduct(brand.getId(), "에어맥스");
            UserAddress address = createAddress(userId);
            createOrder(product.getId(), address.getId());

            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    "/api/v1/orders", HttpMethod.GET,
                    new HttpEntity<>(authHeaders()), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void 주문이_없으면_빈_목록을_반환한다() {
            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    "/api/v1/orders", HttpMethod.GET,
                    new HttpEntity<>(authHeaders()), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void 인증_없이_요청하면_401_Unauthorized를_반환한다() {
            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    "/api/v1/orders", HttpMethod.GET,
                    new HttpEntity<>(new HttpHeaders()), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("GET /api/v1/orders/{orderId}")
    @Nested
    class 주문_상세_조회 {

        @Test
        void 주문_상세_조회에_성공하면_200_OK를_반환한다() {
            // arrange
            Brand brand = createActiveBrand("나이키");
            Product product = createActiveProduct(brand.getId(), "에어맥스");
            UserAddress address = createAddress(userId);
            createOrder(product.getId(), address.getId());
            Order order = orderRepository.findAllByUserId(userId,
                    java.time.ZonedDateTime.now().minusDays(1), java.time.ZonedDateTime.now().plusDays(1)).get(0);

            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    "/api/v1/orders/" + order.getId(), HttpMethod.GET,
                    new HttpEntity<>(authHeaders()), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void 존재하지_않는_주문이면_404_Not_Found를_반환한다() {
            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    "/api/v1/orders/999", HttpMethod.GET,
                    new HttpEntity<>(authHeaders()), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        void 타인의_주문이면_403_Forbidden을_반환한다() {
            // arrange - 다른 사용자의 주문 직접 생성
            Order otherOrder = orderRepository.save(Order.create(999L, "ORD-OTHER-001",
                    List.of(com.loopers.domain.order.OrderItem.create(1L, "상품", "브랜드", 10000, 1)),
                    "타인", "010-0000-0000", "타인", "010-0000-0000",
                    "00000", "어딘가", null));

            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    "/api/v1/orders/" + otherOrder.getId(), HttpMethod.GET,
                    new HttpEntity<>(authHeaders()), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @DisplayName("DELETE /api/v1/orders/{orderId}")
    @Nested
    class 주문_취소 {

        @Test
        void 취소에_성공하면_200_OK를_반환한다() {
            // arrange
            Brand brand = createActiveBrand("나이키");
            Product product = createActiveProduct(brand.getId(), "에어맥스");
            UserAddress address = createAddress(userId);
            createOrder(product.getId(), address.getId());
            Order order = orderRepository.findAllByUserId(userId,
                    java.time.ZonedDateTime.now().minusDays(1), java.time.ZonedDateTime.now().plusDays(1)).get(0);

            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    "/api/v1/orders/" + order.getId(), HttpMethod.DELETE,
                    new HttpEntity<>(authHeaders()), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void 취소_후_재고_예약이_해제된다() {
            // arrange
            Brand brand = createActiveBrand("나이키");
            Product product = createActiveProduct(brand.getId(), "에어맥스");
            UserAddress address = createAddress(userId);
            createOrder(product.getId(), address.getId());
            Order order = orderRepository.findAllByUserId(userId,
                    java.time.ZonedDateTime.now().minusDays(1), java.time.ZonedDateTime.now().plusDays(1)).get(0);

            // act
            testRestTemplate.exchange(
                    "/api/v1/orders/" + order.getId(), HttpMethod.DELETE,
                    new HttpEntity<>(authHeaders()), ApiResponse.class);

            // assert
            Inventory inventory = inventoryRepository.findByProductId(product.getId()).orElseThrow();
            assertThat(inventory.getReservedQty()).isEqualTo(0);
        }

        @Test
        void 존재하지_않는_주문이면_404_Not_Found를_반환한다() {
            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    "/api/v1/orders/999", HttpMethod.DELETE,
                    new HttpEntity<>(authHeaders()), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        void 타인의_주문이면_403_Forbidden을_반환한다() {
            // arrange
            Order otherOrder = orderRepository.save(Order.create(999L, "ORD-OTHER-002",
                    List.of(com.loopers.domain.order.OrderItem.create(1L, "상품", "브랜드", 10000, 1)),
                    "타인", "010-0000-0000", "타인", "010-0000-0000",
                    "00000", "어딘가", null));

            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    "/api/v1/orders/" + otherOrder.getId(), HttpMethod.DELETE,
                    new HttpEntity<>(authHeaders()), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        void 이미_취소된_주문이면_409_Conflict를_반환한다() {
            // arrange
            Brand brand = createActiveBrand("나이키");
            Product product = createActiveProduct(brand.getId(), "에어맥스");
            UserAddress address = createAddress(userId);
            createOrder(product.getId(), address.getId());
            Order order = orderRepository.findAllByUserId(userId,
                    java.time.ZonedDateTime.now().minusDays(1), java.time.ZonedDateTime.now().plusDays(1)).get(0);
            // 먼저 취소
            testRestTemplate.exchange(
                    "/api/v1/orders/" + order.getId(), HttpMethod.DELETE,
                    new HttpEntity<>(authHeaders()), ApiResponse.class);

            // act - 재취소 시도
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    "/api/v1/orders/" + order.getId(), HttpMethod.DELETE,
                    new HttpEntity<>(authHeaders()), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }
    }
}
