package com.loopers.interfaces.api.order;

import com.loopers.infrastructure.member.MemberJpaRepository;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.brand.admin.BrandAdminV1Dto;
import com.loopers.interfaces.api.member.MemberV1Dto;
import com.loopers.interfaces.api.product.admin.ProductAdminV1Dto;
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

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderV1ApiE2ETest {

    private final TestRestTemplate testRestTemplate;
    private final DatabaseCleanUp databaseCleanUp;
    private final MemberJpaRepository memberJpaRepository;

    @Autowired
    public OrderV1ApiE2ETest(TestRestTemplate testRestTemplate, DatabaseCleanUp databaseCleanUp,
                             MemberJpaRepository memberJpaRepository) {
        this.testRestTemplate = testRestTemplate;
        this.databaseCleanUp = databaseCleanUp;
        this.memberJpaRepository = memberJpaRepository;
    }

    @AfterEach
    void tearDown() { databaseCleanUp.truncateAllTables(); }

    private HttpHeaders adminHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Loopers-Ldap", "loopers.admin");
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private Long createBrand(String name) {
        var req = new BrandAdminV1Dto.CreateRequest(name, "설명");
        return testRestTemplate.exchange("/api-admin/v1/brands", HttpMethod.POST,
            new HttpEntity<>(req, adminHeaders()),
            new ParameterizedTypeReference<ApiResponse<BrandAdminV1Dto.BrandResponse>>() {}).getBody().data().id();
    }

    private Long createProduct(String name, int price, Long brandId, int stock) {
        var req = new ProductAdminV1Dto.CreateRequest(name, "설명", price, brandId, stock);
        return testRestTemplate.exchange("/api-admin/v1/products", HttpMethod.POST,
            new HttpEntity<>(req, adminHeaders()),
            new ParameterizedTypeReference<ApiResponse<ProductAdminV1Dto.ProductResponse>>() {}).getBody().data().id();
    }

    private void signupMember() {
        var req = new MemberV1Dto.SignupRequest("testuser", "Test1234!", "테스트유저",
            LocalDate.of(1998, 1, 1), "test@example.com");
        testRestTemplate.exchange("/api/v1/users", HttpMethod.POST, new HttpEntity<>(req),
            new ParameterizedTypeReference<ApiResponse<MemberV1Dto.MemberResponse>>() {});
    }

    private HttpHeaders authHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Loopers-LoginId", "testuser");
        h.set("X-Loopers-LoginPw", "Test1234!");
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> placeOrder(List<OrderV1Dto.OrderItemRequest> items) {
        return testRestTemplate.exchange("/api/v1/orders", HttpMethod.POST,
            new HttpEntity<>(new OrderV1Dto.CreateRequest(items), authHeaders()),
            new ParameterizedTypeReference<>() {});
    }

    @DisplayName("POST /api/v1/orders") @Nested
    class CreateOrder {
        @DisplayName("단일 상품 주문이 성공한다") @Test
        void createsSingleItemOrder() {
            Long brandId = createBrand("나이키");
            Long productId = createProduct("에어맥스", 129000, brandId, 10);
            signupMember();
            var response = placeOrder(List.of(new OrderV1Dto.OrderItemRequest(productId, 2)));
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().totalAmount()).isEqualTo(258000),
                () -> assertThat(response.getBody().data().status()).isEqualTo("CREATED"),
                () -> assertThat(response.getBody().data().items()).hasSize(1)
            );
        }

        @DisplayName("복수 상품 주문이 성공한다") @Test
        void createsMultiItemOrder() {
            Long brandId = createBrand("나이키");
            Long p1 = createProduct("에어맥스", 129000, brandId, 10);
            Long p2 = createProduct("조던", 159000, brandId, 5);
            signupMember();
            var response = placeOrder(List.of(
                new OrderV1Dto.OrderItemRequest(p1, 2), new OrderV1Dto.OrderItemRequest(p2, 1)));
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().totalAmount()).isEqualTo(417000),
                () -> assertThat(response.getBody().data().items()).hasSize(2)
            );
        }

        @DisplayName("재고 부족 시 400을 반환한다") @Test
        void returns400WhenInsufficientStock() {
            Long brandId = createBrand("나이키");
            Long productId = createProduct("에어맥스", 129000, brandId, 1);
            signupMember();
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange("/api/v1/orders",
                HttpMethod.POST, new HttpEntity<>(new OrderV1Dto.CreateRequest(
                    List.of(new OrderV1Dto.OrderItemRequest(productId, 5))), authHeaders()),
                new ParameterizedTypeReference<>() {});
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("인증 헤더 누락 시 400을 반환한다") @Test
        void returns400WhenNoAuth() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange("/api/v1/orders",
                HttpMethod.POST, new HttpEntity<>(new OrderV1Dto.CreateRequest(
                    List.of(new OrderV1Dto.OrderItemRequest(1L, 1)))),
                new ParameterizedTypeReference<>() {});
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("GET /api/v1/orders") @Nested
    class GetMyOrders {
        @DisplayName("기간별 주문 목록을 조회한다") @Test
        void returnsOrdersByDateRange() {
            Long brandId = createBrand("나이키");
            Long productId = createProduct("에어맥스", 129000, brandId, 10);
            signupMember();
            placeOrder(List.of(new OrderV1Dto.OrderItemRequest(productId, 1)));
            String today = LocalDate.now().toString();
            var response = testRestTemplate.exchange(
                "/api/v1/orders?startAt=" + today + "&endAt=" + today, HttpMethod.GET,
                new HttpEntity<>(null, authHeaders()), new ParameterizedTypeReference<ApiResponse<Object>>() {});
            assertTrue(response.getStatusCode().is2xxSuccessful());
        }
    }

    @DisplayName("GET /api/v1/orders/{orderId}") @Nested
    class GetOrder {
        @DisplayName("주문 상세를 조회한다") @Test
        void returnsOrderDetail() {
            Long brandId = createBrand("나이키");
            Long productId = createProduct("에어맥스", 129000, brandId, 10);
            signupMember();
            var orderResponse = placeOrder(List.of(new OrderV1Dto.OrderItemRequest(productId, 2)));
            Long orderId = orderResponse.getBody().data().orderId();
            var response = testRestTemplate.exchange("/api/v1/orders/" + orderId, HttpMethod.GET,
                new HttpEntity<>(null, authHeaders()),
                new ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>>() {});
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().orderId()).isEqualTo(orderId),
                () -> assertThat(response.getBody().data().totalAmount()).isEqualTo(258000),
                () -> assertThat(response.getBody().data().items()).hasSize(1)
            );
        }
    }

    @DisplayName("GET /api-admin/v1/orders") @Nested
    class AdminGetOrders {
        @DisplayName("관리자 주문 목록을 조회한다") @Test
        void returnsAllOrders() {
            Long brandId = createBrand("나이키");
            Long productId = createProduct("에어맥스", 129000, brandId, 10);
            signupMember();
            placeOrder(List.of(new OrderV1Dto.OrderItemRequest(productId, 1)));
            var response = testRestTemplate.exchange("/api-admin/v1/orders?page=0&size=20",
                HttpMethod.GET, null, new ParameterizedTypeReference<ApiResponse<Object>>() {});
            assertTrue(response.getStatusCode().is2xxSuccessful());
        }
    }

    @DisplayName("GET /api-admin/v1/orders/{orderId}") @Nested
    class AdminGetOrder {
        @DisplayName("관리자 주문 상세를 조회한다") @Test
        void returnsOrderDetailForAdmin() {
            Long brandId = createBrand("나이키");
            Long productId = createProduct("에어맥스", 129000, brandId, 10);
            signupMember();
            var orderResponse = placeOrder(List.of(new OrderV1Dto.OrderItemRequest(productId, 1)));
            Long orderId = orderResponse.getBody().data().orderId();
            var response = testRestTemplate.exchange("/api-admin/v1/orders/" + orderId,
                HttpMethod.GET, null, new ParameterizedTypeReference<ApiResponse<Object>>() {});
            assertTrue(response.getStatusCode().is2xxSuccessful());
        }
    }
}
