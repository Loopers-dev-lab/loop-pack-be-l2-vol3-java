package com.loopers.interfaces.api.payment;

import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductService;
import com.loopers.infrastructure.payment.PgSimulatorClient;
import com.loopers.infrastructure.payment.PgSimulatorRequest;
import com.loopers.infrastructure.payment.PgSimulatorResponse;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.order.OrderV1Dto;
import com.loopers.interfaces.api.user.UserV1Dto;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 역할: {@code POST /api/v1/payments/callback}에 대해 시크릿 헤더/바디 검증이
 * HTTP 레벨에서 401로 드러나는지 E2E로 확인한다 (06 §11.3, {@code ErrorType.UNAUTHORIZED}).
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "pg.simulator.callback-secret=pay-cb-secret-e2e")
@Import(MySqlTestContainersConfig.class)
class PaymentV1PaymentCallbackSecretE2ETest {

    private static final String LOGIN_ID = "paycbuser";
    private static final String ENDPOINT_PAYMENTS = "/api/v1/payments";
    private static final String ENDPOINT_CALLBACK = "/api/v1/payments/callback";

    @Autowired
    private TestRestTemplate testRestTemplate;
    @Autowired
    private DatabaseCleanUp databaseCleanUp;
    @Autowired
    private BrandService brandService;
    @Autowired
    private ProductService productService;
    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @MockBean
    private PgSimulatorClient pgSimulatorClient;

    private Long productId;

    @BeforeEach
    void setUp() {
        when(pgSimulatorClient.requestPayment(any(PgSimulatorRequest.class)))
                .thenReturn(new PgSimulatorResponse("cb-e2e-tx"));

        UserV1Dto.SignUpRequest signUp = new UserV1Dto.SignUpRequest(
                LOGIN_ID, "SecurePass1!", "paycb@example.com", "1990-01-15", "MALE");
        testRestTemplate.exchange("/api/v1/users", HttpMethod.POST, new HttpEntity<>(signUp),
                new ParameterizedTypeReference<ApiResponse<UserV1Dto.SignUpResponse>>() {
                });
        var brand = brandService.registerBrand("PayCbBrand");
        var product = productService.registerProduct(brand.getId(), "PayCbItem", new BigDecimal("11000"), 12);
        productId = product.getId();
    }

    @AfterEach
    void tearDown() {
        circuitBreakerRegistry.circuitBreaker("pgCircuit").transitionToClosedState();
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders authHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Loopers-LoginId", LOGIN_ID);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private Long createOrderedOrderViaApi() {
        OrderV1Dto.CreateOrderRequest req = new OrderV1Dto.CreateOrderRequest(
                List.of(new OrderV1Dto.OrderItemRequest(productId, 1, null)), null);
        ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> res = testRestTemplate.exchange(
                "/api/v1/orders", HttpMethod.POST, new HttpEntity<>(req, authHeaders()),
                new ParameterizedTypeReference<>() {
                });
        return res.getBody().data().id();
    }

    private HttpHeaders callbackHeadersWithSecret(String secret) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (secret != null) {
            h.set("X-PG-Callback-Secret", secret);
        }
        return h;
    }

    @Test
    @DisplayName("콜백 시크릿 헤더가 없으면 401이다.")
    void paymentCallback_whenSecretMissing_shouldReturn401() {
        Long orderId = createOrderedOrderViaApi();
        PaymentV1Dto.PaymentRequest payReq = new PaymentV1Dto.PaymentRequest(orderId, "SAMSUNG", "1");
        testRestTemplate.exchange(
                ENDPOINT_PAYMENTS, HttpMethod.POST, new HttpEntity<>(payReq, authHeaders()),
                new ParameterizedTypeReference<ApiResponse<PaymentV1Dto.PaymentResponse>>() {
                });

        String body = """
                {"paymentId":"x","orderId":%d,"success":false,"failureReason":"X"}
                """.formatted(orderId);
        ResponseEntity<Void> res = testRestTemplate.exchange(
                ENDPOINT_CALLBACK, HttpMethod.POST,
                new HttpEntity<>(body, callbackHeadersWithSecret(null)),
                Void.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("콜백 시크릿이 틀리면 401이다.")
    void paymentCallback_whenSecretWrong_shouldReturn401() {
        Long orderId = createOrderedOrderViaApi();
        PaymentV1Dto.PaymentRequest payReq = new PaymentV1Dto.PaymentRequest(orderId, "SAMSUNG", "1");
        testRestTemplate.exchange(
                ENDPOINT_PAYMENTS, HttpMethod.POST, new HttpEntity<>(payReq, authHeaders()),
                new ParameterizedTypeReference<ApiResponse<PaymentV1Dto.PaymentResponse>>() {
                });

        String body = """
                {"paymentId":"x","orderId":%d,"success":false,"failureReason":"X"}
                """.formatted(orderId);
        ResponseEntity<Void> res = testRestTemplate.exchange(
                ENDPOINT_CALLBACK, HttpMethod.POST,
                new HttpEntity<>(body, callbackHeadersWithSecret("wrong-secret")),
                Void.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("콜백 시크릿이 맞으면 200이다.")
    void paymentCallback_whenSecretCorrect_shouldReturn200() {
        Long orderId = createOrderedOrderViaApi();
        PaymentV1Dto.PaymentRequest payReq = new PaymentV1Dto.PaymentRequest(orderId, "SAMSUNG", "1");
        testRestTemplate.exchange(
                ENDPOINT_PAYMENTS, HttpMethod.POST, new HttpEntity<>(payReq, authHeaders()),
                new ParameterizedTypeReference<ApiResponse<PaymentV1Dto.PaymentResponse>>() {
                });

        String body = """
                {"paymentId":"x","orderId":%d,"success":false,"failureReason":"LIMIT"}
                """.formatted(orderId);
        ResponseEntity<Void> res = testRestTemplate.exchange(
                ENDPOINT_CALLBACK, HttpMethod.POST,
                new HttpEntity<>(body, callbackHeadersWithSecret("pay-cb-secret-e2e")),
                Void.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
