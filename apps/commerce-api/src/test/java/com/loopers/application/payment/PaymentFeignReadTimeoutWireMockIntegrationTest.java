package com.loopers.application.payment;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductValidationRequest;
import com.loopers.domain.product.Quantity;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 역할: PG Feign readTimeout이 실제로 발생할 때의 동작을 검증한다.
 * - MockBean으로 클라이언트를 바꾸면 지연 응답으로 타임아웃을 재현하기 어려워, WireMock + 실제 Feign을 쓴다.
 * - 타임아웃 후에도 PENDING 저장·응답 일관성(06 Phase 1, checklist)을 본다.
 */
@SpringBootTest(properties = {
        "feign.client.config.default.readTimeout=500",
        "feign.client.config.default.connectTimeout=500",
        "resilience4j.retry.instances.pgRetry.max-attempts=1"
})
@Import(MySqlTestContainersConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PaymentFeignReadTimeoutWireMockIntegrationTest {

    private static final WireMockServer WM = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());

    static {
        WM.start();
    }

    @DynamicPropertySource
    static void registerPgUrl(DynamicPropertyRegistry registry) {
        registry.add("pg.simulator.url", () -> "http://localhost:" + WM.port());
    }

    @AfterAll
    static void stopWireMock() {
        WM.stop();
    }

    private static final long USER_ID = 1L;

    @Autowired
    private PaymentFacade paymentFacade;
    @Autowired
    private OrderService orderService;
    @Autowired
    private BrandService brandService;
    @Autowired
    private ProductService productService;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private DatabaseCleanUp databaseCleanUp;
    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @AfterEach
    void tearDown() {
        WM.resetAll();
        circuitBreakerRegistry.circuitBreaker("pgCircuit").transitionToClosedState();
        databaseCleanUp.truncateAllTables();
    }

    /** PG 응답이 readTimeout보다 늦을 때: 예외 처리 후에도 DB·응답이 PENDING으로 남는지 확인. */
    @Test
    @DisplayName("Feign readTimeout이 나도 PENDING은 저장되고 200 응답 흐름과 동일하게 유지된다.")
    void requestPayment_whenFeignReadTimeout_shouldKeepPendingInDb() {
        // given — 응답이 readTimeout(500ms)보다 늦게 도착
        WM.stubFor(post(urlPathEqualTo("/api/v1/payments"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withFixedDelay(1_500)
                        .withBody("{\"transactionId\":\"late-tx\"}")));

        Long brandId = brandService.registerBrand("wm-timeout-brand").getId();
        ProductModel product = productService.registerProduct(brandId, "wm-timeout-p", new BigDecimal("4000"), 7);
        OrderModel order = orderService.create(USER_ID, List.of(
                new ProductValidationRequest(product.getId(), Quantity.of(1), null)));

        // when
        PaymentInfo info = paymentFacade.requestPayment(USER_ID, order.getId(), "SAMSUNG", "4242");

        // then
        assertThat(info.status()).isEqualTo("PENDING");
        assertThat(info.orderId()).isEqualTo(order.getId());
        assertThat(paymentRepository.existsByOrderIdAndStatus(order.getId(), PaymentStatus.PENDING)).isTrue();
        WM.verify(postRequestedFor(urlPathEqualTo("/api/v1/payments")));
    }
}
