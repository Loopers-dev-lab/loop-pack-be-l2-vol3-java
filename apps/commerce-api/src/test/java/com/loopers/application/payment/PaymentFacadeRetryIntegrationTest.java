package com.loopers.application.payment;

import com.loopers.domain.brand.BrandService;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductValidationRequest;
import com.loopers.domain.product.Quantity;
import com.loopers.infrastructure.payment.PgSimulatorClient;
import com.loopers.infrastructure.payment.PgSimulatorRequest;
import com.loopers.infrastructure.payment.PgSimulatorResponse;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import feign.FeignException;
import feign.Request;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 역할: {@code @Retry}(pgRetry)가 재시도 대상 오류에만 동작하는지 통합 검증한다.
 * - 일시적 RuntimeException·Feign 503 → 설정된 횟수만큼 PG 재호출.
 * - Feign 400 → 재시도 없이 1회 호출 (비재시도 예외 정책).
 */
@SpringBootTest
@Import(MySqlTestContainersConfig.class)
class PaymentFacadeRetryIntegrationTest {

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
    private DatabaseCleanUp databaseCleanUp;
    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @MockBean
    private PgSimulatorClient pgSimulatorClient;

    @AfterEach
    void tearDown() {
        circuitBreakerRegistry.circuitBreaker("pgCircuit").transitionToClosedState();
        databaseCleanUp.truncateAllTables();
    }

    private OrderModel createOrderedOrder() {
        Long brandId = brandService.registerBrand("retry-brand").getId();
        ProductModel product = productService.registerProduct(brandId, "retry-p", new BigDecimal("5000"), 8);
        return orderService.create(USER_ID, List.of(
                new ProductValidationRequest(product.getId(), Quantity.of(1), null)));
    }

    /** RuntimeException 같은 일시 오류에 Retry가 개입해 최종 성공까지 여러 번 호출. */
    @Test
    @DisplayName("PG가 일시 실패했다가 성공하면 maxAttempts만큼 호출된다.")
    void requestPayment_whenPgFailsTransiently_shouldRetryUpToConfiguredAttempts() {
        // given
        AtomicInteger calls = new AtomicInteger();
        when(pgSimulatorClient.requestPayment(any(PgSimulatorRequest.class))).thenAnswer(inv -> {
            if (calls.incrementAndGet() < 3) {
                throw new RuntimeException("transient");
            }
            return new PgSimulatorResponse("retry-ok");
        });
        OrderModel order = createOrderedOrder();

        // when
        PaymentInfo info = paymentFacade.requestPayment(USER_ID, order.getId(), "SAMSUNG", "1");

        // then
        assertThat(info.status()).isEqualTo("PENDING");
        verify(pgSimulatorClient, times(3)).requestPayment(any(PgSimulatorRequest.class));
    }

    /** HTTP 503은 재시도 가능한 장애로 간주되어 동일하게 maxAttempts 검증. */
    @Test
    @DisplayName("Feign 503(Service Unavailable)는 재시도 설정만큼 PG를 호출한다.")
    void requestPayment_whenPgReturns503_shouldRetryUpToConfiguredAttempts() {
        // given
        Request feignRequest = Request.create(
                Request.HttpMethod.POST,
                "/api/v1/payments",
                Collections.emptyMap(),
                null,
                StandardCharsets.UTF_8);
        AtomicInteger calls = new AtomicInteger();
        when(pgSimulatorClient.requestPayment(any(PgSimulatorRequest.class))).thenAnswer(inv -> {
            if (calls.incrementAndGet() < 3) {
                throw new FeignException.ServiceUnavailable("503", feignRequest, null, null);
            }
            return new PgSimulatorResponse("503-ok");
        });
        OrderModel order = createOrderedOrder();

        // when
        PaymentInfo info = paymentFacade.requestPayment(USER_ID, order.getId(), "SAMSUNG", "1");

        // then
        assertThat(info.status()).isEqualTo("PENDING");
        verify(pgSimulatorClient, times(3)).requestPayment(any(PgSimulatorRequest.class));
    }

    /** 4xx는 재시도하지 않아 호출 수 1회로 고정(잘못된 요청 반복 방지). */
    @Test
    @DisplayName("Feign 400(BadRequest)는 재시도하지 않고 1회만 PG를 호출한다.")
    void requestPayment_whenPgReturns400_shouldNotRetry() {
        // given
        Request feignRequest = Request.create(
                Request.HttpMethod.POST,
                "/api/v1/payments",
                Collections.emptyMap(),
                null,
                StandardCharsets.UTF_8);
        when(pgSimulatorClient.requestPayment(any(PgSimulatorRequest.class)))
                .thenThrow(new FeignException.BadRequest("bad body", feignRequest, null, null));
        OrderModel order = createOrderedOrder();

        // when
        PaymentInfo info = paymentFacade.requestPayment(USER_ID, order.getId(), "SAMSUNG", "1");

        // then
        assertThat(info.status()).isEqualTo("PENDING");
        verify(pgSimulatorClient, times(1)).requestPayment(any(PgSimulatorRequest.class));
    }

    /** Phase 6: Retry 소진 시 fallback으로 예외가 상위로 전파되지 않고 PENDING 유지. */
    @Test
    @DisplayName("PG가 계속 실패하면 재시도 소진 후에도 PENDING 응답이며 호출은 maxAttempts와 같다.")
    void requestPayment_whenPgAlwaysFails_shouldFallbackWithoutThrowing() {
        when(pgSimulatorClient.requestPayment(any(PgSimulatorRequest.class)))
                .thenThrow(new RuntimeException("persistent failure"));
        OrderModel order = createOrderedOrder();

        PaymentInfo info = paymentFacade.requestPayment(USER_ID, order.getId(), "SAMSUNG", "1");

        assertThat(info.status()).isEqualTo("PENDING");
        verify(pgSimulatorClient, times(3)).requestPayment(any(PgSimulatorRequest.class));
    }
}
