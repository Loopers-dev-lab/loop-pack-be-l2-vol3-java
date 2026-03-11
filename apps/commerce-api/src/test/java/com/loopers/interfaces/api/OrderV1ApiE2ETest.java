package com.loopers.interfaces.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.user.UserModel;
import com.loopers.infrastructure.brand.BrandEntity;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.order.OrderJpaRepository;
import com.loopers.infrastructure.product.ProductEntity;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.interfaces.api.order.OrderV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import java.time.LocalDate;
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
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderV1ApiE2ETest {

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private OrderJpaRepository orderJpaRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private BrandEntity savedBrand;
    private UserModel savedUser;
    private static final String LOGIN_ID = "testuser";
    private static final String LOGIN_PW = "Password1!";

    @BeforeEach
    void setUp() {
        savedBrand = brandJpaRepository.save(createBrandEntity("나이키", "스포츠 브랜드"));
        savedUser = userJpaRepository.save(
            UserModel.create(LOGIN_ID, passwordEncoder.encode(LOGIN_PW), LocalDate.of(1990, 1, 1), "테스트유저", "test@test.com")
        );
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("POST /api/v1/orders")
    class CreateOrder {

        @Test
        @DisplayName("주문을 생성하면, 주문 정보를 반환한다")
        void success_create_order() {
            ProductEntity product1 = productJpaRepository.save(
                createProductEntity("상품1", savedBrand.getId(), 10000, 100, 0)
            );
            ProductEntity product2 = productJpaRepository.save(
                createProductEntity("상품2", savedBrand.getId(), 20000, 50, 0)
            );

            String url = "/api/v1/orders";
            HttpHeaders headers = createAuthHeaders();
            OrderV1Dto.CreateOrderRequest request = new OrderV1Dto.CreateOrderRequest(
                List.of(
                    new OrderV1Dto.OrderItemRequest(product1.getId(), 2),
                    new OrderV1Dto.OrderItemRequest(product2.getId(), 1)
                )
            );
            var responseType = new ParameterizedTypeReference<ApiResponse<OrderV1Dto.CreateOrderResponse>>() {};

            ResponseEntity<ApiResponse<OrderV1Dto.CreateOrderResponse>> response =
                testRestTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(request, headers), responseType);

            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().id()).isNotNull(),
                () -> assertThat(response.getBody().data().userId()).isEqualTo(savedUser.getId()),
                () -> assertThat(response.getBody().data().status()).isEqualTo(OrderStatus.ORDERED),
                () -> assertThat(response.getBody().data().totalPrice()).isEqualTo(40000) // 10000*2 + 20000*1
            );
        }

        @Test
        @DisplayName("주문 후 재고가 차감된다")
        void success_decrease_stock_after_order() {
            ProductEntity product = productJpaRepository.save(
                createProductEntity("상품1", savedBrand.getId(), 10000, 100, 0)
            );

            String url = "/api/v1/orders";
            HttpHeaders headers = createAuthHeaders();
            OrderV1Dto.CreateOrderRequest request = new OrderV1Dto.CreateOrderRequest(
                List.of(new OrderV1Dto.OrderItemRequest(product.getId(), 10))
            );
            var responseType = new ParameterizedTypeReference<ApiResponse<OrderV1Dto.CreateOrderResponse>>() {};

            testRestTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(request, headers), responseType);

            ProductEntity updatedProduct = productJpaRepository.findById(product.getId()).orElseThrow();
            assertThat(updatedProduct.getStock()).isEqualTo(90);
        }

        @Test
        @DisplayName("재고가 부족하면, 400 응답을 반환한다")
        void fail_when_stock_not_enough() {
            ProductEntity product = productJpaRepository.save(
                createProductEntity("상품1", savedBrand.getId(), 10000, 5, 0)
            );

            String url = "/api/v1/orders";
            HttpHeaders headers = createAuthHeaders();
            OrderV1Dto.CreateOrderRequest request = new OrderV1Dto.CreateOrderRequest(
                List.of(new OrderV1Dto.OrderItemRequest(product.getId(), 10))
            );
            var responseType = new ParameterizedTypeReference<ApiResponse<OrderV1Dto.CreateOrderResponse>>() {};

            ResponseEntity<ApiResponse<OrderV1Dto.CreateOrderResponse>> response =
                testRestTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(request, headers), responseType);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("존재하지 않는 상품을 주문하면, 400 응답을 반환한다")
        void fail_when_product_not_found() {
            String url = "/api/v1/orders";
            HttpHeaders headers = createAuthHeaders();
            OrderV1Dto.CreateOrderRequest request = new OrderV1Dto.CreateOrderRequest(
                List.of(new OrderV1Dto.OrderItemRequest(99999L, 1))
            );
            var responseType = new ParameterizedTypeReference<ApiResponse<OrderV1Dto.CreateOrderResponse>>() {};

            ResponseEntity<ApiResponse<OrderV1Dto.CreateOrderResponse>> response =
                testRestTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(request, headers), responseType);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("주문 상품 목록이 비어있으면, 400 응답을 반환한다")
        void fail_when_order_items_empty() {
            String url = "/api/v1/orders";
            HttpHeaders headers = createAuthHeaders();
            OrderV1Dto.CreateOrderRequest request = new OrderV1Dto.CreateOrderRequest(List.of());
            var responseType = new ParameterizedTypeReference<ApiResponse<OrderV1Dto.CreateOrderResponse>>() {};

            ResponseEntity<ApiResponse<OrderV1Dto.CreateOrderResponse>> response =
                testRestTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(request, headers), responseType);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("인증 헤더가 없으면, 401 응답을 반환한다")
        void fail_when_unauthorized() {
            ProductEntity product = productJpaRepository.save(
                createProductEntity("상품1", savedBrand.getId(), 10000, 100, 0)
            );

            String url = "/api/v1/orders";
            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");
            OrderV1Dto.CreateOrderRequest request = new OrderV1Dto.CreateOrderRequest(
                List.of(new OrderV1Dto.OrderItemRequest(product.getId(), 1))
            );
            var responseType = new ParameterizedTypeReference<ApiResponse<OrderV1Dto.CreateOrderResponse>>() {};

            ResponseEntity<ApiResponse<OrderV1Dto.CreateOrderResponse>> response =
                testRestTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(request, headers), responseType);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    private HttpHeaders createAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(LoopersHeaders.X_LOOPERS_LOGIN_ID, LOGIN_ID);
        headers.set(LoopersHeaders.X_LOOPERS_LOGIN_PW, LOGIN_PW);
        headers.set("Content-Type", "application/json");
        return headers;
    }

    private BrandEntity createBrandEntity(String name, String description) {
        try {
            BrandEntity entity = BrandEntity.class.getDeclaredConstructor().newInstance();

            var nameField = BrandEntity.class.getDeclaredField("name");
            nameField.setAccessible(true);
            nameField.set(entity, name);

            var descField = BrandEntity.class.getDeclaredField("description");
            descField.setAccessible(true);
            descField.set(entity, description);

            return entity;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private ProductEntity createProductEntity(String name, Long brandId, int price, int stock, int likeCount) {
        try {
            ProductEntity entity = ProductEntity.class.getDeclaredConstructor().newInstance();

            var nameField = ProductEntity.class.getDeclaredField("name");
            nameField.setAccessible(true);
            nameField.set(entity, name);

            var brandIdField = ProductEntity.class.getDeclaredField("refBrandId");
            brandIdField.setAccessible(true);
            brandIdField.set(entity, brandId);

            var priceField = ProductEntity.class.getDeclaredField("price");
            priceField.setAccessible(true);
            priceField.set(entity, price);

            var stockField = ProductEntity.class.getDeclaredField("stock");
            stockField.setAccessible(true);
            stockField.set(entity, stock);

            var likeCountField = ProductEntity.class.getDeclaredField("likeCount");
            likeCountField.setAccessible(true);
            likeCountField.set(entity, likeCount);

            return entity;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
