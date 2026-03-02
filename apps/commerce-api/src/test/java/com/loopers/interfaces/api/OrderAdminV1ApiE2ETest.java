package com.loopers.interfaces.api;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductStatus;
import com.loopers.domain.user.UserModel;
import com.loopers.infrastructure.brand.BrandJpaRepository;
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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderAdminV1ApiE2ETest {

    private static final String ENDPOINT_ADMIN_ORDERS = "/api-admin/v1/orders";

    private final TestRestTemplate testRestTemplate;
    private final UserJpaRepository userJpaRepository;
    private final BrandJpaRepository brandJpaRepository;
    private final ProductJpaRepository productJpaRepository;
    private final OrderJpaRepository orderJpaRepository;
    private final PasswordEncoder passwordEncoder;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public OrderAdminV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        UserJpaRepository userJpaRepository,
        BrandJpaRepository brandJpaRepository,
        ProductJpaRepository productJpaRepository,
        OrderJpaRepository orderJpaRepository,
        PasswordEncoder passwordEncoder,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.userJpaRepository = userJpaRepository;
        this.brandJpaRepository = brandJpaRepository;
        this.productJpaRepository = productJpaRepository;
        this.orderJpaRepository = orderJpaRepository;
        this.passwordEncoder = passwordEncoder;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private OrderModel createOrder() {
        String encodedPassword = passwordEncoder.encode("Test1234!");
        UserModel user = userJpaRepository.save(
            UserModel.createWithEncodedPassword("testuser", encodedPassword, "홍길동", LocalDate.of(1990, 1, 15), "test@example.com")
        );
        BrandModel brand = brandJpaRepository.save(new BrandModel("나이키", "스포츠 의류 및 신발 브랜드"));
        ProductModel product = productJpaRepository.save(new ProductModel(brand, "에어맥스", 150000L, "나이키 에어맥스", 10, ProductStatus.ON_SALE));

        // 유저 API를 통해 주문 생성
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", "testuser");
        headers.set("X-Loopers-LoginPw", "Test1234!");

        PlaceOrderRequest request = new PlaceOrderRequest(
            List.of(new PlaceOrderItemRequest(product.getId(), 2))
        );

        ParameterizedTypeReference<ApiResponse<OrderDetailResponse>> responseType = new ParameterizedTypeReference<>() {};
        ResponseEntity<ApiResponse<OrderDetailResponse>> response = testRestTemplate.exchange(
            "/api/v1/orders",
            HttpMethod.POST,
            new HttpEntity<>(request, headers),
            responseType
        );
        Long orderId = response.getBody().data().id();

        return orderJpaRepository.findDetailById(orderId).orElseThrow();
    }

    @DisplayName("GET /api-admin/v1/orders - 관리자 주문 목록 조회")
    @Nested
    class GetAll {

        @DisplayName("주문이 존재하면, 페이징된 주문 목록을 반환한다.")
        @Test
        void returnsPagedOrders_whenOrdersExist() {
            // arrange
            createOrder();

            // act
            ParameterizedTypeReference<ApiResponse<Map<String, Object>>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_ADMIN_ORDERS + "?page=0&size=20",
                HttpMethod.GET,
                null,
                responseType
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat((List<?>) response.getBody().data().get("content")).hasSize(1)
            );
        }
    }

    @DisplayName("GET /api-admin/v1/orders/{orderId} - 관리자 주문 상세 조회")
    @Nested
    class GetOrder {

        @DisplayName("존재하는 주문 ID가 주어지면, 주문 상세를 반환한다.")
        @Test
        void returnsOrder_whenIdExists() {
            // arrange
            OrderModel order = createOrder();

            // act
            ParameterizedTypeReference<ApiResponse<OrderDetailResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<OrderDetailResponse>> response = testRestTemplate.exchange(
                ENDPOINT_ADMIN_ORDERS + "/" + order.getId(),
                HttpMethod.GET,
                null,
                responseType
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().totalAmount()).isEqualTo(300000L),
                () -> assertThat(response.getBody().data().items()).hasSize(1)
            );
        }

        @DisplayName("존재하지 않는 주문 ID가 주어지면, NOT_FOUND 응답을 받는다.")
        @Test
        void returnsNotFound_whenIdDoesNotExist() {
            // act
            ParameterizedTypeReference<ApiResponse<OrderDetailResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<OrderDetailResponse>> response = testRestTemplate.exchange(
                ENDPOINT_ADMIN_ORDERS + "/999",
                HttpMethod.GET,
                null,
                responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    record PlaceOrderRequest(
        List<PlaceOrderItemRequest> items
    ) {}

    record PlaceOrderItemRequest(
        Long productId,
        int quantity
    ) {}

    record OrderDetailResponse(
        Long id,
        Long userId,
        Long totalAmount,
        String orderedAt,
        List<OrderItemResponse> items
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
