package com.loopers.interfaces.api.order;

import com.loopers.interfaces.api.brand.dto.BrandCreateRequest;
import com.loopers.interfaces.api.order.dto.DeliveryAddressUpdateRequest;
import com.loopers.interfaces.api.order.dto.OrderCreateRequest;
import com.loopers.interfaces.api.order.dto.OrderDetailResponse;
import com.loopers.interfaces.api.order.dto.OrderSummaryResponse;
import com.loopers.interfaces.api.product.dto.ProductCreateRequest;
import com.loopers.interfaces.api.product.dto.ProductDetailResponse;
import com.loopers.interfaces.api.user.dto.UserRegisterRequest;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(MySqlTestContainersConfig.class)
class OrderApiE2ETest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private static final String ORDER_URL = "/api/v1/orders";
    private static final String LOGIN_ID = "e2euser1";
    private static final String PASSWORD = "Password1!";

    @BeforeEach
    void setUp() {
        databaseCleanUp.truncateAllTables();
        registerUser(LOGIN_ID, PASSWORD, "홍길동");
        createBrand("나이키", "스포츠");
        createProduct(1L, "운동화", 50000, 100);
    }

    @Nested
    @DisplayName("E2E: 주문 전체 플로우")
    class OrderFlowE2E {

        @Test
        @DisplayName("주문 생성 → 조회 → 배송지 변경 → 취소 → 재고 복원 확인")
        void fullOrderFlow() {
            // Step 1: 주문 생성
            var orderRequest = new OrderCreateRequest(
                    List.of(new OrderCreateRequest.OrderItemRequest(1L, 2)),
                    "홍길동", "서울시 강남구", "문 앞에 놓아주세요",
                    "CARD", LocalDate.now().plusDays(3)
            );

            HttpHeaders authHeaders = createAuthHeaders(LOGIN_ID, PASSWORD);
            authHeaders.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<Void> createResponse = restTemplate.exchange(
                    ORDER_URL,
                    HttpMethod.POST,
                    new HttpEntity<>(orderRequest, authHeaders),
                    Void.class
            );
            assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

            // Step 2: 주문 목록 조회
            ResponseEntity<String> listResponse = restTemplate.exchange(
                    ORDER_URL,
                    HttpMethod.GET,
                    new HttpEntity<>(createAuthHeaders(LOGIN_ID, PASSWORD)),
                    String.class
            );
            assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(listResponse.getBody()).contains("PAYMENT_COMPLETED");

            // Step 3: 주문 상세 조회
            ResponseEntity<OrderDetailResponse> detailResponse = restTemplate.exchange(
                    ORDER_URL + "/1",
                    HttpMethod.GET,
                    new HttpEntity<>(createAuthHeaders(LOGIN_ID, PASSWORD)),
                    OrderDetailResponse.class
            );
            assertThat(detailResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(detailResponse.getBody().receiverName()).isEqualTo("홍길동");
            assertThat(detailResponse.getBody().address()).isEqualTo("서울시 강남구");

            // Step 4: 배송지 변경
            var addressRequest = new DeliveryAddressUpdateRequest("부산시 해운대구");
            HttpHeaders addressHeaders = createAuthHeaders(LOGIN_ID, PASSWORD);
            addressHeaders.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<Void> addressResponse = restTemplate.exchange(
                    ORDER_URL + "/1/delivery-address",
                    HttpMethod.PUT,
                    new HttpEntity<>(addressRequest, addressHeaders),
                    Void.class
            );
            assertThat(addressResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

            // Step 5: 변경된 배송지 확인
            ResponseEntity<OrderDetailResponse> updatedDetail = restTemplate.exchange(
                    ORDER_URL + "/1",
                    HttpMethod.GET,
                    new HttpEntity<>(createAuthHeaders(LOGIN_ID, PASSWORD)),
                    OrderDetailResponse.class
            );
            assertThat(updatedDetail.getBody().address()).isEqualTo("부산시 해운대구");

            // Step 6: 주문 취소
            ResponseEntity<Void> cancelResponse = restTemplate.exchange(
                    ORDER_URL + "/1/cancel",
                    HttpMethod.POST,
                    new HttpEntity<>(createAuthHeaders(LOGIN_ID, PASSWORD)),
                    Void.class
            );
            assertThat(cancelResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

            // Step 7: 취소 상태 확인
            ResponseEntity<OrderDetailResponse> cancelledDetail = restTemplate.exchange(
                    ORDER_URL + "/1",
                    HttpMethod.GET,
                    new HttpEntity<>(createAuthHeaders(LOGIN_ID, PASSWORD)),
                    OrderDetailResponse.class
            );
            assertThat(cancelledDetail.getBody().status()).isEqualTo("CANCELLED");

            // Step 8: 재고 복원 확인 (원래 100, 2개 주문 → 98, 취소 → 100)
            ResponseEntity<ProductDetailResponse> productResponse = restTemplate.getForEntity(
                    "/api/v1/products/1",
                    ProductDetailResponse.class
            );
            assertThat(productResponse.getBody().stock()).isEqualTo(100);
        }
    }

    @Nested
    @DisplayName("E2E: 관리자 주문 관리")
    class AdminOrderE2E {

        @Test
        @DisplayName("관리자 전체 주문 조회")
        void admin_getAllOrders() {
            // given - 주문 생성
            createOrder();

            // when
            HttpHeaders adminHeaders = new HttpHeaders();
            adminHeaders.set("X-Loopers-Ldap", "loopers.admin");

            ResponseEntity<String> response = restTemplate.exchange(
                    "/api-admin/v1/orders",
                    HttpMethod.GET,
                    new HttpEntity<>(adminHeaders),
                    String.class
            );

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).contains("PAYMENT_COMPLETED");
        }

        @Test
        @DisplayName("관리자 주문 상세 조회")
        void admin_getOrderDetail() {
            // given
            createOrder();

            // when
            HttpHeaders adminHeaders = new HttpHeaders();
            adminHeaders.set("X-Loopers-Ldap", "loopers.admin");

            ResponseEntity<OrderDetailResponse> response = restTemplate.exchange(
                    "/api-admin/v1/orders/1",
                    HttpMethod.GET,
                    new HttpEntity<>(adminHeaders),
                    OrderDetailResponse.class
            );

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().receiverName()).isEqualTo("홍길동");
        }
    }

    private HttpHeaders createAuthHeaders(String loginId, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", loginId);
        headers.set("X-Loopers-LoginPw", password);
        return headers;
    }

    private HttpHeaders createAdminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-Ldap", "loopers.admin");
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private void registerUser(String loginId, String password, String name) {
        var request = new UserRegisterRequest(loginId, password, name,
                LocalDate.of(1990, 5, 15), "test@example.com");
        restTemplate.postForEntity("/api/v1/users", request, Void.class);
    }

    private void createBrand(String name, String description) {
        var request = new BrandCreateRequest(name, description);
        restTemplate.exchange("/api-admin/v1/brands", HttpMethod.POST,
                new HttpEntity<>(request, createAdminHeaders()), Void.class);
    }

    private void createProduct(Long brandId, String name, int price, int stock) {
        var request = new ProductCreateRequest(brandId, name, price, null, stock, "설명");
        restTemplate.exchange("/api-admin/v1/products", HttpMethod.POST,
                new HttpEntity<>(request, createAdminHeaders()), Void.class);
    }

    private void createOrder() {
        var request = new OrderCreateRequest(
                List.of(new OrderCreateRequest.OrderItemRequest(1L, 2)),
                "홍길동", "서울시 강남구", "문 앞에 놓아주세요",
                "CARD", LocalDate.now().plusDays(3)
        );
        HttpHeaders headers = createAuthHeaders(LOGIN_ID, PASSWORD);
        headers.setContentType(MediaType.APPLICATION_JSON);
        restTemplate.exchange(ORDER_URL, HttpMethod.POST,
                new HttpEntity<>(request, headers), Void.class);
    }
}
