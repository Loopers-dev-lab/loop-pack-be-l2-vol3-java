package com.loopers.interfaces.api;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentGatewayException;
import com.loopers.domain.payment.PaymentGatewayRetryableException;
import com.loopers.interfaces.api.brand.AdminBrandV1Dto;
import com.loopers.interfaces.api.cart.CartV1Dto;
import com.loopers.interfaces.api.order.OrderV1Dto;
import com.loopers.interfaces.api.payment.PaymentV1Dto;
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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("결제 Fallback E2E 테스트")
class PaymentV1ApiFallbackE2ETest {

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @MockBean
    private PaymentGateway paymentGateway;

    private Long orderId;

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", "testUser1");
        headers.set("X-Loopers-LoginPw", "Abcd1234!");
        headers.set("Content-Type", "application/json");
        return headers;
    }

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-Ldap", "loopers.admin");
        headers.set("Content-Type", "application/json");
        return headers;
    }

    @BeforeEach
    void setUp() {
        // 회원가입
        testRestTemplate.exchange("/api/v1/users", HttpMethod.POST,
            new HttpEntity<>(new UserV1Dto.SignupRequest("testUser1", "Abcd1234!", "홍길동",
                LocalDate.of(1995, 3, 15), "test@example.com")),
            new ParameterizedTypeReference<ApiResponse<UserV1Dto.SignupResponse>>() {});

        // 브랜드 생성
        ResponseEntity<ApiResponse<AdminBrandV1Dto.BrandResponse>> brandResp = testRestTemplate.exchange(
            "/api-admin/v1/brands", HttpMethod.POST,
            new HttpEntity<>(new AdminBrandV1Dto.CreateRequest("나이키"), adminHeaders()),
            new ParameterizedTypeReference<>() {});
        Long brandId = brandResp.getBody().data().id();

        // 상품 생성
        ResponseEntity<ApiResponse<AdminProductV1Dto.ProductResponse>> productResp = testRestTemplate.exchange(
            "/api-admin/v1/products", HttpMethod.POST,
            new HttpEntity<>(new AdminProductV1Dto.CreateRequest(brandId, "에어맥스", 50000, 100), adminHeaders()),
            new ParameterizedTypeReference<>() {});
        Long productId = productResp.getBody().data().id();

        // 장바구니에 담기
        testRestTemplate.exchange("/api/v1/cart/items", HttpMethod.POST,
            new HttpEntity<>(new CartV1Dto.AddRequest(productId, 1), authHeaders()),
            new ParameterizedTypeReference<ApiResponse<CartV1Dto.CartItemResponse>>() {});

        // 주문 생성 (장바구니 기반이 아닌 직접 주문)
        List<OrderV1Dto.OrderItemRequest> items = List.of(
            new OrderV1Dto.OrderItemRequest(productId, 1)
        );
        ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> orderResp = testRestTemplate.exchange(
            "/api/v1/orders", HttpMethod.POST,
            new HttpEntity<>(new OrderV1Dto.CreateOrderRequest(items, null), authHeaders()),
            new ParameterizedTypeReference<>() {});
        orderId = orderResp.getBody().data().orderId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("PG 일시적 장애(timeout/5xx) 시, ")
    @Nested
    class RetryableFallback {

        @DisplayName("HTTP 200 + PENDING 상태로 응답한다.")
        @Test
        void returns200WithPendingStatus_whenPgRetryableFails() {
            when(paymentGateway.requestPayment(anyLong(), anyLong(), any(CardType.class), anyString(), anyInt()))
                .thenThrow(new PaymentGatewayRetryableException("PG 요청 타임아웃"));

            PaymentV1Dto.PaymentRequest request = new PaymentV1Dto.PaymentRequest(orderId, CardType.SAMSUNG, "1234-5678-9012-3456");
            ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> response = testRestTemplate.exchange(
                "/api/v1/payments", HttpMethod.POST,
                new HttpEntity<>(request, authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(response.getBody().data().status()).isEqualTo("PENDING")
            );
        }
    }

    @DisplayName("PG 비재시도 장애(4xx/계약 오류) 시, ")
    @Nested
    class NonRetryableFallback {

        @DisplayName("HTTP 500으로 응답한다.")
        @Test
        void returns500_whenPgNonRetryableFails() {
            when(paymentGateway.requestPayment(anyLong(), anyLong(), any(CardType.class), anyString(), anyInt()))
                .thenThrow(new PaymentGatewayException("PG 요청 실패: 잘못된 카드 정보입니다."));

            PaymentV1Dto.PaymentRequest request = new PaymentV1Dto.PaymentRequest(orderId, CardType.SAMSUNG, "1234-5678-9012-3456");
            ResponseEntity<Map> response = testRestTemplate.exchange(
                "/api/v1/payments", HttpMethod.POST,
                new HttpEntity<>(request, authHeaders()),
                Map.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
