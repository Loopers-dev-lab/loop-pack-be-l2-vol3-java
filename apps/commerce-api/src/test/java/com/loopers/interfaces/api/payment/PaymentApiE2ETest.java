package com.loopers.interfaces.api.payment;

import com.loopers.domain.address.UserAddress;
import com.loopers.domain.address.UserAddressRepository;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.inventory.Inventory;
import com.loopers.domain.inventory.InventoryRepository;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.point.PointAccount;
import com.loopers.domain.point.PointAccountRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.order.OrderRequest;
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

import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class PaymentApiE2ETest {

    @Autowired private TestRestTemplate testRestTemplate;
    @Autowired private BrandRepository brandRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private InventoryRepository inventoryRepository;
    @Autowired private UserAddressRepository userAddressRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private PointAccountRepository pointAccountRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

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
        pointAccountRepository.save(PointAccount.create(userId));
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", "testuser");
        headers.set("X-Loopers-LoginPw", "Hx7!mK2@");
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private Long createOrderAndGetId() {
        Brand brand = brandRepository.save(Brand.create("나이키", "나이키 설명"));
        Product product = Product.create(brand.getId(), "에어맥스", "에어맥스 설명", 50000);
        Product savedProduct = productRepository.save(product);
        inventoryRepository.save(Inventory.create(savedProduct.getId(), 100));
        UserAddress address = UserAddress.create(userId, "홍길동", "010-1234-5678",
                "12345", "서울시 강남구 테헤란로 123", "4층 401호");
        UserAddress savedAddress = userAddressRepository.save(address);

        OrderRequest.CreateOrderRequest orderRequest = new OrderRequest.CreateOrderRequest(
                List.of(new OrderRequest.OrderItemRequest(savedProduct.getId(), 2)),
                List.of(),
                savedAddress.getId(),
                "010-1234-5678",
                null, 0, "CARD");
        testRestTemplate.exchange("/api/v1/orders", HttpMethod.POST,
                new HttpEntity<>(orderRequest, authHeaders()), ApiResponse.class);

        Order order = orderRepository.findAllByUserId(userId,
                ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(1)).get(0);
        return order.getId();
    }

    @DisplayName("POST /api/v1/orders/{orderId}/pay")
    @Nested
    class 결제_요청 {

        @Test
        void 이미_PAID_상태이면_409_Conflict를_반환한다() {
            // arrange — 1단계 트랜잭션으로 주문 즉시 PAID 확정
            Long orderId = createOrderAndGetId();
            PaymentRequest.PayRequest request = new PaymentRequest.PayRequest("CARD", null);

            // act — 이미 PAID인 주문에 결제 요청
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    "/api/v1/orders/" + orderId + "/pay", HttpMethod.POST,
                    new HttpEntity<>(request, authHeaders()), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        void 존재하지_않는_주문이면_404_Not_Found를_반환한다() {
            // act
            PaymentRequest.PayRequest request = new PaymentRequest.PayRequest("CARD", null);
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    "/api/v1/orders/999/pay", HttpMethod.POST,
                    new HttpEntity<>(request, authHeaders()), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        void 이미_결제된_주문이면_409_Conflict를_반환한다() {
            // arrange
            Long orderId = createOrderAndGetId();
            PaymentRequest.PayRequest request = new PaymentRequest.PayRequest("CARD", null);
            testRestTemplate.exchange("/api/v1/orders/" + orderId + "/pay", HttpMethod.POST,
                    new HttpEntity<>(request, authHeaders()), ApiResponse.class);

            // act - 재결제 시도
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    "/api/v1/orders/" + orderId + "/pay", HttpMethod.POST,
                    new HttpEntity<>(request, authHeaders()), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        void 인증_없이_요청하면_401_Unauthorized를_반환한다() {
            // act
            PaymentRequest.PayRequest request = new PaymentRequest.PayRequest("CARD", null);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    "/api/v1/orders/1/pay", HttpMethod.POST,
                    new HttpEntity<>(request, headers), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        void 결제_성공_후_재고가_확정된다() {
            // arrange
            Long orderId = createOrderAndGetId();
            PaymentRequest.PayRequest request = new PaymentRequest.PayRequest("CARD", null);

            // act
            testRestTemplate.exchange("/api/v1/orders/" + orderId + "/pay", HttpMethod.POST,
                    new HttpEntity<>(request, authHeaders()), ApiResponse.class);

            // assert - 재고가 줄고 reserved가 0
            Order order = orderRepository.findById(orderId).orElseThrow();
            Long productId = order.getItems().get(0).getProductId();
            Inventory inventory = inventoryRepository.findByProductId(productId).orElseThrow();
            assertThat(inventory.getReservedQty()).isEqualTo(0);
            assertThat(inventory.getQuantity()).isEqualTo(98); // 100 - 2
        }
    }

    @DisplayName("PUT /api/v1/orders/{orderId}/discount")
    @Nested
    class 할인_적용 {

        @Test
        void 이미_PAID_상태이면_할인적용_시_409_Conflict를_반환한다() {
            // arrange — 1단계 트랜잭션으로 주문 즉시 PAID 확정
            Long orderId = createOrderAndGetId();
            PointAccount account = pointAccountRepository.findByUserId(userId).orElseThrow();
            account.charge(10000);
            pointAccountRepository.save(account);

            PaymentRequest.ApplyDiscountRequest request =
                    new PaymentRequest.ApplyDiscountRequest(null, 5000);

            // act — 이미 PAID인 주문에 할인 적용 시도
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    "/api/v1/orders/" + orderId + "/discount", HttpMethod.PUT,
                    new HttpEntity<>(request, authHeaders()), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        void 포인트_잔액_부족이면_409_Conflict를_반환한다() {
            // arrange
            Long orderId = createOrderAndGetId();
            PaymentRequest.ApplyDiscountRequest request =
                    new PaymentRequest.ApplyDiscountRequest(null, 50000);

            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    "/api/v1/orders/" + orderId + "/discount", HttpMethod.PUT,
                    new HttpEntity<>(request, authHeaders()), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }
    }
}
