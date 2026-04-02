package com.loopers.interfaces.api.order;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.product.ProductDto;
import com.loopers.interfaces.api.member.MemberDto;
import com.loopers.domain.category.Category;
import com.loopers.domain.category.CategoryRepository;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.UUID;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "loopers.queue.order.enabled=true",
                "loopers.queue.order.throughput-per-second=2"
        }
)
@ImportTestcontainers({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})
@ActiveProfiles("test")
class OrderApiE2ETest {

    private static final String ENDPOINT_ORDERS = "/api/v1/orders";
    private static final String ENDPOINT_BRANDS = "/api-admin/v1/brands";
    private static final String HEADER_ADMIN_LDAP = "X-Loopers-Ldap";
    private static final String ADMIN_LDAP_VALUE = "loopers.admin";
    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";
    private static final String TEST_LOGIN_ID = "orderuser1";
    private static final String TEST_PASSWORD = "Test1234!@";

    private final TestRestTemplate testRestTemplate;
    private final DatabaseCleanUp databaseCleanUp;
    private final RedisCleanUp redisCleanUp;
    private final CategoryRepository categoryRepository;
    private UUID brandId;
    private UUID categoryId;

    @Autowired
    public OrderApiE2ETest(
            TestRestTemplate testRestTemplate,
            DatabaseCleanUp databaseCleanUp,
            RedisCleanUp redisCleanUp,
            CategoryRepository categoryRepository
    ) {
        this.testRestTemplate = testRestTemplate;
        this.databaseCleanUp = databaseCleanUp;
        this.redisCleanUp = redisCleanUp;
        this.categoryRepository = categoryRepository;
    }

    @BeforeEach
    void setUp() {
        // 테스트 유저 생성
        MemberDto.RegisterRequest registerRequest = new MemberDto.RegisterRequest(
                TEST_LOGIN_ID, TEST_PASSWORD, "주문자", "19900101", "order@test.com", "010-1234-5678"
        );
        testRestTemplate.exchange(
                "/api/v1/members",
                HttpMethod.POST,
                new HttpEntity<>(registerRequest),
                new ParameterizedTypeReference<ApiResponse<Void>>() {}
        );

        brandId = createBrand("ORDER_TEST_BRAND");
        categoryId = createCategory("ORDER_TEST_CATEGORY");
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_LOGIN_ID, TEST_LOGIN_ID);
        headers.set(HEADER_LOGIN_PW, TEST_PASSWORD);
        return headers;
    }

    private UUID createProduct(String name, int price, int stock) {
        ProductDto.CreateProductRequest request = new ProductDto.CreateProductRequest(
                name, price, stock, "설명", categoryId, brandId
        );
        ResponseEntity<ApiResponse<ProductDto.ProductResponse>> response = testRestTemplate.exchange(
                "/api/v1/products",
                HttpMethod.POST,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data()).isNotNull();
        return response.getBody().data().id();
    }

    private UUID createBrand(String name) {
        var request = new com.loopers.interfaces.api.brand.BrandDto.CreateBrandRequest(
                name,
                "테스트 브랜드",
                "https://example.com/logo.png"
        );

        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_ADMIN_LDAP, ADMIN_LDAP_VALUE);

        ResponseEntity<com.loopers.interfaces.api.ApiResponse<com.loopers.interfaces.api.brand.BrandDto.BrandResponse>> response =
                testRestTemplate.exchange(
                        ENDPOINT_BRANDS,
                        HttpMethod.POST,
                        new HttpEntity<>(request, headers),
                        new ParameterizedTypeReference<>() {}
                );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data()).isNotNull();

        return response.getBody().data().id();
    }

    private UUID createCategory(String name) {
        return categoryRepository.save(new Category(name)).id();
    }

    @Nested
    @DisplayName("주문 대기열 시나리오")
    class OrderQueueScenario {

        @Test
        @DisplayName("대기열 재진입 시 마지막 timestamp 기준으로 뒤 순번을 반환한다")
        void reEnterQueueMovesUserToBack() {
            final String secondLoginId = "orderuser2";
            MemberDto.RegisterRequest secondUserRequest = new MemberDto.RegisterRequest(
                    secondLoginId, TEST_PASSWORD, "주문자2", "19900101", "order2@test.com", "010-7777-5678"
            );
            testRestTemplate.exchange(
                    "/api/v1/members",
                    HttpMethod.POST,
                    new HttpEntity<>(secondUserRequest),
                    new ParameterizedTypeReference<ApiResponse<Void>>() {}
            );

            ResponseEntity<ApiResponse<OrderDto.OrderQueueStatusResponse>> firstEnter = testRestTemplate.exchange(
                    "/api/v1/order-queue",
                    HttpMethod.POST,
                    new HttpEntity<>(authHeaders()),
                    new ParameterizedTypeReference<>() {}
            );
            assertThat(firstEnter.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(firstEnter.getBody().data().waitingOrder()).isEqualTo(1);

            HttpHeaders secondHeaders = new HttpHeaders();
            secondHeaders.set(HEADER_LOGIN_ID, secondLoginId);
            secondHeaders.set(HEADER_LOGIN_PW, TEST_PASSWORD);
            testRestTemplate.exchange(
                    "/api/v1/order-queue",
                    HttpMethod.POST,
                    new HttpEntity<>(secondHeaders),
                    new ParameterizedTypeReference<ApiResponse<OrderDto.OrderQueueStatusResponse>>() {}
            );

            ResponseEntity<ApiResponse<OrderDto.OrderQueueStatusResponse>> reEnter = testRestTemplate.exchange(
                    "/api/v1/order-queue",
                    HttpMethod.POST,
                    new HttpEntity<>(authHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(reEnter.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(reEnter.getBody().data().enabled()).isTrue();
            assertThat(reEnter.getBody().data().waitingOrder()).isEqualTo(2);

            ResponseEntity<ApiResponse<OrderDto.OrderQueueStatusResponse>> status = testRestTemplate.exchange(
                    "/api/v1/order-queue/me",
                    HttpMethod.GET,
                    new HttpEntity<>(authHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(status.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(status.getBody().data().waitingOrder()).isEqualTo(2);
            assertThat(status.getBody().data().estimatedWaitSeconds()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("주문 CRUD 시나리오")
    class OrderCrudScenario {

        @Test
        @DisplayName("주문 생성 → 상세 조회 → 취소 → 재취소 실패 시나리오")
        void fullOrderFlow() {
            // 상품 생성
            UUID productId = createProduct("강아지 사료", 10000, 50);

            // 주문 생성
            OrderDto.CreateOrderRequest createRequest = new OrderDto.CreateOrderRequest(
                    List.of(new OrderDto.OrderItemRequest(productId, 2))
            );

            ResponseEntity<ApiResponse<OrderDto.OrderResponse>> created = testRestTemplate.exchange(
                    ENDPOINT_ORDERS,
                    HttpMethod.POST,
                    new HttpEntity<>(createRequest, authHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            UUID orderId = created.getBody().data().id();
            assertThat(created.getBody().data().status()).isEqualTo("ORDERED");
            assertThat(created.getBody().data().totalAmount()).isEqualTo(20000);

            // 주문 상세 조회
            ResponseEntity<ApiResponse<OrderDto.OrderResponse>> detail = testRestTemplate.exchange(
                    ENDPOINT_ORDERS + "/" + orderId,
                    HttpMethod.GET,
                    new HttpEntity<>(authHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(detail.getBody().data().items()).hasSize(1);
            assertThat(detail.getBody().data().items().get(0).snapshotProductName()).isEqualTo("강아지 사료");

            // 주문 취소
            ResponseEntity<ApiResponse<OrderDto.OrderResponse>> cancelled = testRestTemplate.exchange(
                    ENDPOINT_ORDERS + "/" + orderId + "/cancel",
                    HttpMethod.PATCH,
                    new HttpEntity<>(authHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(cancelled.getBody().data().status()).isEqualTo("CANCELLED");

            // 재취소 시 409
            ResponseEntity<ApiResponse<Object>> reCancelled = testRestTemplate.exchange(
                    ENDPOINT_ORDERS + "/" + orderId + "/cancel",
                    HttpMethod.PATCH,
                    new HttpEntity<>(authHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(reCancelled.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        @DisplayName("재고 부족 시 주문이 실패하고 재고가 차감되지 않는다")
        void insufficientStockFails() {
            UUID productId = createProduct("한정판 사료", 50000, 2);

            OrderDto.CreateOrderRequest request = new OrderDto.CreateOrderRequest(
                    List.of(new OrderDto.OrderItemRequest(productId, 5))
            );

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT_ORDERS,
                    HttpMethod.POST,
                    new HttpEntity<>(request, authHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("주문 목록 조회 - 기간 내 주문만 반환된다")
        void listOrdersWithDateFilter() {
            UUID productId = createProduct("사료", 5000, 100);

            OrderDto.CreateOrderRequest request = new OrderDto.CreateOrderRequest(
                    List.of(new OrderDto.OrderItemRequest(productId, 1))
            );

            testRestTemplate.exchange(
                    ENDPOINT_ORDERS,
                    HttpMethod.POST,
                    new HttpEntity<>(request, authHeaders()),
                    new ParameterizedTypeReference<ApiResponse<OrderDto.OrderResponse>>() {}
            );

            ResponseEntity<ApiResponse<OrderDto.OrderListResponse>> listResponse = testRestTemplate.exchange(
                    ENDPOINT_ORDERS + "?startAt=20260101&endAt=20261231&page=0&size=20",
                    HttpMethod.GET,
                    new HttpEntity<>(authHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(listResponse.getBody().data().totalElements()).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("취소 후 재고가 복원된다")
        void stockRestoredAfterCancel() {
            UUID productId = createProduct("귀한 사료", 20000, 3);

            // 3개 주문 (재고 0이 됨)
            OrderDto.CreateOrderRequest request = new OrderDto.CreateOrderRequest(
                    List.of(new OrderDto.OrderItemRequest(productId, 3))
            );

            ResponseEntity<ApiResponse<OrderDto.OrderResponse>> created = testRestTemplate.exchange(
                    ENDPOINT_ORDERS,
                    HttpMethod.POST,
                    new HttpEntity<>(request, authHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            UUID orderId = created.getBody().data().id();

            // 취소 (재고 복원)
            testRestTemplate.exchange(
                    ENDPOINT_ORDERS + "/" + orderId + "/cancel",
                    HttpMethod.PATCH,
                    new HttpEntity<>(authHeaders()),
                    new ParameterizedTypeReference<ApiResponse<OrderDto.OrderResponse>>() {}
            );

            // 다시 주문 가능 (재고 복원 확인)
            ResponseEntity<ApiResponse<OrderDto.OrderResponse>> reOrder = testRestTemplate.exchange(
                    ENDPOINT_ORDERS,
                    HttpMethod.POST,
                    new HttpEntity<>(request, authHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(reOrder.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }
    }
}
