package com.loopers.interfaces.api;

import com.loopers.interfaces.api.brand.AdminBrandV1Dto;
import com.loopers.interfaces.api.order.AdminOrderV1Dto;
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
class AdminOrderV1ApiE2ETest {

    private static final String ADMIN_ORDER_ENDPOINT = "/api-admin/v1/orders";
    private static final String ORDER_ENDPOINT = "/api/v1/orders";
    private static final String ADMIN_BRAND_ENDPOINT = "/api-admin/v1/brands";
    private static final String ADMIN_PRODUCT_ENDPOINT = "/api-admin/v1/products";
    private static final String SIGNUP_ENDPOINT = "/api/v1/users";

    private final TestRestTemplate testRestTemplate;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public AdminOrderV1ApiE2ETest(TestRestTemplate testRestTemplate, DatabaseCleanUp databaseCleanUp) {
        this.testRestTemplate = testRestTemplate;
        this.databaseCleanUp = databaseCleanUp;
    }

    private Long productId;

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

        AdminProductV1Dto.CreateRequest productRequest = new AdminProductV1Dto.CreateRequest(brandId, "에어맥스", 129000, 100);
        ResponseEntity<ApiResponse<AdminProductV1Dto.ProductResponse>> productResponse = testRestTemplate.exchange(
            ADMIN_PRODUCT_ENDPOINT, HttpMethod.POST, new HttpEntity<>(productRequest, adminHeaders()),
            new ParameterizedTypeReference<>() {}
        );
        productId = productResponse.getBody().data().id();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private OrderV1Dto.OrderDetailResponse createOrder(int quantity) {
        OrderV1Dto.CreateOrderRequest request = new OrderV1Dto.CreateOrderRequest(
            List.of(new OrderV1Dto.OrderItemRequest(productId, quantity)), null
        );
        ResponseEntity<ApiResponse<OrderV1Dto.OrderDetailResponse>> response = testRestTemplate.exchange(
            ORDER_ENDPOINT, HttpMethod.POST, new HttpEntity<>(request, authHeaders()),
            new ParameterizedTypeReference<>() {}
        );
        return response.getBody().data();
    }

    @DisplayName("GET /api-admin/v1/orders")
    @Nested
    class GetAllOrders {

        @DisplayName("주문이 존재하면, 페이지 결과를 반환한다.")
        @Test
        void returnsPageResult_whenOrdersExist() {
            createOrder(2);
            createOrder(1);
            createOrder(3);

            ResponseEntity<ApiResponse<AdminOrderV1Dto.OrderPageResponse>> response = testRestTemplate.exchange(
                ADMIN_ORDER_ENDPOINT + "?page=0&size=2", HttpMethod.GET, new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().content()).hasSize(2),
                () -> assertThat(response.getBody().data().totalElements()).isEqualTo(3),
                () -> assertThat(response.getBody().data().totalPages()).isEqualTo(2),
                () -> assertThat(response.getBody().data().content().get(0).userId()).isNotNull()
            );
        }

        @DisplayName("어드민 인증이 없으면, 401 UNAUTHORIZED를 반환한다.")
        @Test
        void returnsUnauthorized_whenNotAdmin() {
            ResponseEntity<ApiResponse<AdminOrderV1Dto.OrderPageResponse>> response = testRestTemplate.exchange(
                ADMIN_ORDER_ENDPOINT, HttpMethod.GET, new HttpEntity<>(new HttpHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("GET /api-admin/v1/orders/{orderId}")
    @Nested
    class GetOrderDetail {

        @DisplayName("존재하는 주문이면, 주문 상세를 반환한다.")
        @Test
        void returnsOrderDetail_whenOrderExists() {
            OrderV1Dto.OrderDetailResponse created = createOrder(2);

            ResponseEntity<ApiResponse<AdminOrderV1Dto.OrderDetailResponse>> response = testRestTemplate.exchange(
                ADMIN_ORDER_ENDPOINT + "/" + created.orderId(), HttpMethod.GET, new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().orderId()).isEqualTo(created.orderId()),
                () -> assertThat(response.getBody().data().userId()).isNotNull(),
                () -> assertThat(response.getBody().data().items()).hasSize(1),
                () -> assertThat(response.getBody().data().items().get(0).productName()).isEqualTo("에어맥스"),
                () -> assertThat(response.getBody().data().items().get(0).brandName()).isEqualTo("나이키")
            );
        }

        @DisplayName("존재하지 않는 주문이면, 404 NOT_FOUND를 반환한다.")
        @Test
        void returnsNotFound_whenOrderDoesNotExist() {
            ResponseEntity<ApiResponse<AdminOrderV1Dto.OrderDetailResponse>> response = testRestTemplate.exchange(
                ADMIN_ORDER_ENDPOINT + "/999", HttpMethod.GET, new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
