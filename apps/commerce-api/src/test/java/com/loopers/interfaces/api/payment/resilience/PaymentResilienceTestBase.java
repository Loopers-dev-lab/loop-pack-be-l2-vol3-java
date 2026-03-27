package com.loopers.interfaces.api.payment.resilience;

import com.loopers.application.payment.PaymentService;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.gateway.PgType;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.order.OrderRequest;
import com.loopers.interfaces.api.payment.PaymentRequest;
import com.loopers.interfaces.api.payment.PaymentV1Dto;
import com.loopers.support.E2ETestFixture;
import com.loopers.utils.DatabaseCleanUp;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(E2ETestFixture.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
abstract class PaymentResilienceTestBase {

    protected static final String PAYMENT_ENDPOINT = "/api/v1/payments";
    protected static final String LOGIN_ID = "resilienceuser";
    protected static final String PASSWORD = "Test1234!";

    @Autowired
    protected TestRestTemplate testRestTemplate;

    @Autowired
    protected E2ETestFixture fixture;

    @Autowired
    protected DatabaseCleanUp databaseCleanUp;

    @Autowired
    protected CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    protected PaymentService paymentService;

    @Value("${payment.toss.base-url}")
    protected String tossBaseUrl;

    @Value("${payment.nice.base-url}")
    protected String niceBaseUrl;

    protected final RestTemplate chaosClient = new RestTemplate();

    protected Long productId;

    @BeforeEach
    void setUpBase() {
        databaseCleanUp.truncateAllTables();
        chaosClient.delete(tossBaseUrl + "/test/reset");
        chaosClient.delete(niceBaseUrl + "/test/reset");
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(CircuitBreaker::reset);

        fixture.signUp(LOGIN_ID, PASSWORD, "테스터", "resilience@test.com");
        Long brandId = fixture.registerBrand("테스트브랜드", "테스트");
        productId = fixture.registerProduct(brandId, "운동화", new BigDecimal("50000"), 9999, "테스트");
    }

    // Chaos helpers

    protected void setChaosToss(String mode) {
        chaosClient.put(tossBaseUrl + "/chaos/mode?mode=" + mode, null);
    }

    protected void setChaosToss(String mode, String params) {
        chaosClient.put(tossBaseUrl + "/chaos/mode?mode=" + mode + "&" + params, null);
    }

    protected void setChaosNice(String mode) {
        chaosClient.put(niceBaseUrl + "/chaos/mode?mode=" + mode, null);
    }

    // Circuit breaker helpers

    protected CircuitBreaker.State getCircuitBreakerState(String name) {
        return circuitBreakerRegistry.circuitBreaker(name).getState();
    }

    // Payment helpers

    protected ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> requestPayment(PgType pgType) {
        Long orderId = fixture.placeOrder(
                List.of(new OrderRequest.PlaceItem(productId, 1)),
                LOGIN_ID, PASSWORD);

        PaymentRequest.Request request = new PaymentRequest.Request(
                orderId, CardType.SAMSUNG, "1234-5678-9012-3456", pgType);

        return testRestTemplate.exchange(
                PAYMENT_ENDPOINT, HttpMethod.POST,
                new HttpEntity<>(request, fixture.userHeaders(LOGIN_ID, PASSWORD)),
                new ParameterizedTypeReference<>() {}
        );
    }

    protected Long createOrderForPayment() {
        return fixture.placeOrder(
                List.of(new OrderRequest.PlaceItem(productId, 1)),
                LOGIN_ID, PASSWORD);
    }

    protected ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> requestPaymentWithOrder(
            Long orderId, PgType pgType) {
        PaymentRequest.Request request = new PaymentRequest.Request(
                orderId, CardType.SAMSUNG, "1234-5678-9012-3456", pgType);

        return testRestTemplate.exchange(
                PAYMENT_ENDPOINT, HttpMethod.POST,
                new HttpEntity<>(request, fixture.userHeaders(LOGIN_ID, PASSWORD)),
                new ParameterizedTypeReference<>() {}
        );
    }

    protected ResponseEntity<ApiResponse<List<PgType>>> getAvailableMethods() {
        return testRestTemplate.exchange(
                PAYMENT_ENDPOINT + "/available-methods", HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
        );
    }

    protected Payment getPaymentByOrderId(Long orderId) {
        return paymentService.getLatestPaymentByOrderId(orderId).orElse(null);
    }
}
