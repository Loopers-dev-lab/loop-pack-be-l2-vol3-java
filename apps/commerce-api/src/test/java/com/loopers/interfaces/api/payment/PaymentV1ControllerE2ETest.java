package com.loopers.interfaces.api.payment;

import com.loopers.application.order.OrderApp;
import com.loopers.application.order.OrderInfo;
import com.loopers.application.order.OrderItemCommand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.utils.DatabaseCleanUp;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "pg.simulator.url=http://localhost:8092",
        "spring.cloud.openfeign.client.config.pg-client.read-timeout=300",
        "resilience4j.circuitbreaker.instances.pg-request.sliding-window-size=5",
        "resilience4j.circuitbreaker.instances.pg-request.minimum-number-of-calls=3",
        "resilience4j.circuitbreaker.instances.pg-request.failure-rate-threshold=60",
        "resilience4j.circuitbreaker.instances.pg-request.slow-call-duration-threshold=10s",
        "resilience4j.circuitbreaker.instances.pg-request.wait-duration-in-open-state=1s"
    }
)
@AutoConfigureWireMock(port = 8092)
@DisplayName("PaymentV1Controller E2E 테스트")
class PaymentV1ControllerE2ETest {

    private static final String PAYMENTS_URL = "/api/v1/payments";
    private static final Long MEMBER_ID = 1L;
    private static final String PRODUCT_ID = "prode2e";
    private static final BigDecimal PRICE = new BigDecimal("100000");
    private static final String TRANSACTION_KEY = "txKey-e2e-001";

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private OrderApp orderApp;
    @Autowired private BrandService brandService;
    @Autowired private ProductRepository productRepository;
    @Autowired private CircuitBreakerRegistry circuitBreakerRegistry;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    @BeforeEach
    void setUp() {
        reset();
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
        brandService.createBrand("nike", "Nike");
        productRepository.save(ProductModel.create(PRODUCT_ID, 1L, "Test Product", PRICE, 100));
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private OrderInfo createOrder() {
        return orderApp.createOrder(MEMBER_ID, List.of(new OrderItemCommand(PRODUCT_ID, 1)));
    }

    private void stubPgAccepted() {
        stubFor(post(urlEqualTo("/api/v1/payments"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"transactionKey\":\"" + TRANSACTION_KEY + "\",\"status\":\"PENDING\"}")));
    }

    private ResponseEntity<ApiResponse<PaymentResponse>> requestPayment(Long orderId) {
        PaymentRequest request = new PaymentRequest(MEMBER_ID, orderId, CardType.SAMSUNG, "1234-5678-9012-3456", PRICE);
        return restTemplate.exchange(
            PAYMENTS_URL, HttpMethod.POST,
            new HttpEntity<>(request),
            new ParameterizedTypeReference<>() {}
        );
    }

    private ResponseEntity<ApiResponse<Void>> sendCallback(String transactionKey, String pgStatus, long amount) {
        Map<String, Object> body = Map.of("transactionKey", transactionKey, "status", pgStatus, "amount", amount);
        return restTemplate.exchange(
            PAYMENTS_URL + "/callback", HttpMethod.POST,
            new HttpEntity<>(body),
            new ParameterizedTypeReference<>() {}
        );
    }

    private ResponseEntity<ApiResponse<PaymentDetailResponse>> getPayment(Long paymentId) {
        return restTemplate.exchange(
            PAYMENTS_URL + "/" + paymentId + "?memberId=" + MEMBER_ID,
            HttpMethod.GET, null,
            new ParameterizedTypeReference<>() {}
        );
    }

    private ResponseEntity<ApiResponse<PaymentDetailResponse>> syncPayment(Long paymentId) {
        return restTemplate.exchange(
            PAYMENTS_URL + "/" + paymentId + "/sync?memberId=" + MEMBER_ID,
            HttpMethod.POST, null,
            new ParameterizedTypeReference<>() {}
        );
    }

    @Test
    @DisplayName("1. 결제 요청 성공 → Payment REQUESTED 응답")
    void requestPayment_success_returnsRequested() {
        OrderInfo order = createOrder();
        stubPgAccepted();

        ResponseEntity<ApiResponse<PaymentResponse>> response = requestPayment(order.id());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().data().status()).isEqualTo(PaymentStatus.REQUESTED);
    }

    @Test
    @DisplayName("2. PG 콜백 COMPLETED → Payment COMPLETED + Order PAID")
    void callback_completed_updatesPaymentAndOrder() {
        OrderInfo order = createOrder();
        stubPgAccepted();
        Long paymentId = requestPayment(order.id()).getBody().data().id();

        sendCallback(TRANSACTION_KEY, "SUCCESS", PRICE.longValue());

        assertThat(getPayment(paymentId).getBody().data().status()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(orderApp.getMyOrder(MEMBER_ID, order.orderId()).status()).isEqualTo("PAID");
    }

    @Test
    @DisplayName("3. PG 콜백 FAILED → Payment FAILED")
    void callback_failed_marksPaymentFailed() {
        OrderInfo order = createOrder();
        stubPgAccepted();
        Long paymentId = requestPayment(order.id()).getBody().data().id();

        sendCallback(TRANSACTION_KEY, "FAILED", PRICE.longValue());

        assertThat(getPayment(paymentId).getBody().data().status()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    @DisplayName("4. PG Timeout → Payment PENDING 유지")
    void requestPayment_pgTimeout_keepsPending() {
        OrderInfo order = createOrder();
        stubFor(post(urlEqualTo("/api/v1/payments"))
            .willReturn(aResponse()
                .withStatus(200)
                .withFixedDelay(600)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"transactionKey\":\"" + TRANSACTION_KEY + "\",\"status\":\"PENDING\"}")));

        ResponseEntity<ApiResponse<PaymentResponse>> response = requestPayment(order.id());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().data().status()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    @DisplayName("5. CB Open → Fallback → Payment PENDING 응답 (서버 다운 아님)")
    void requestPayment_cbOpen_returnsPending() {
        OrderInfo order = createOrder();
        circuitBreakerRegistry.circuitBreaker("pg-request").transitionToOpenState();

        ResponseEntity<ApiResponse<PaymentResponse>> response = requestPayment(order.id());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().data().status()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    @DisplayName("6. 수동 sync (PENDING → COMPLETED) → Order PAID")
    void syncPayment_pendingToCompleted_paysOrder() {
        OrderInfo order = createOrder();
        stubFor(post(urlEqualTo("/api/v1/payments"))
            .willReturn(aResponse().withStatus(500)));
        Long paymentId = requestPayment(order.id()).getBody().data().id();

        reset();
        stubFor(get(urlPathEqualTo("/api/v1/payments"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"orderId\":\"" + order.id() + "\",\"transactions\":[{\"transactionKey\":\"" + TRANSACTION_KEY + "\",\"status\":\"SUCCESS\"}]}")));

        ResponseEntity<ApiResponse<PaymentDetailResponse>> syncResp = syncPayment(paymentId);

        assertThat(syncResp.getBody().data().status()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(orderApp.getMyOrder(MEMBER_ID, order.orderId()).status()).isEqualTo("PAID");
    }

    @Test
    @DisplayName("7. 콜백 중복 수신 → 두 번째 호출 시 400 반환 (이중 처리 방지)")
    void callback_duplicate_secondCallReturns400() {
        OrderInfo order = createOrder();
        stubPgAccepted();
        requestPayment(order.id());
        sendCallback(TRANSACTION_KEY, "SUCCESS", PRICE.longValue());

        ResponseEntity<ApiResponse<Void>> secondResp = sendCallback(TRANSACTION_KEY, "SUCCESS", PRICE.longValue());

        assertThat(secondResp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("8. 위조 콜백 (없는 pgTransactionKey) → 404")
    void callback_unknownTransactionKey_returns404() {
        ResponseEntity<ApiResponse<Void>> response = sendCallback("unknown-key", "SUCCESS", 100000L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("9. 동일 orderId 중복 결제 요청 → 409 CONFLICT")
    void requestPayment_duplicateOrderId_returns409() {
        OrderInfo order = createOrder();
        stubPgAccepted();
        requestPayment(order.id());

        reset();
        stubPgAccepted();
        ResponseEntity<ApiResponse<PaymentResponse>> secondResp = requestPayment(order.id());

        assertThat(secondResp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("10. Order CANCELED 후 COMPLETED 콜백 → Payment COMPLETED, Order CANCELED 유지")
    void callback_orderCanceled_paymentCompletedOrderUnchanged() {
        OrderInfo order = createOrder();
        stubPgAccepted();
        Long paymentId = requestPayment(order.id()).getBody().data().id();

        orderApp.cancelOrder(MEMBER_ID, order.orderId());
        sendCallback(TRANSACTION_KEY, "SUCCESS", PRICE.longValue());

        assertThat(getPayment(paymentId).getBody().data().status()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(orderApp.getMyOrder(MEMBER_ID, order.orderId()).status()).isEqualTo("CANCELED");
    }
}
