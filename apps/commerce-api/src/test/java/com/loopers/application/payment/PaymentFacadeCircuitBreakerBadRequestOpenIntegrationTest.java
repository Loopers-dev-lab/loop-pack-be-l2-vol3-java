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
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Feign 4xx(비재시도)가 연속되면 CB 실패로 집계되어 Open 될 수 있음 (06 §14 Phase 4 예외 집계).
 */
@SpringBootTest
@Import(MySqlTestContainersConfig.class)
@TestPropertySource(
        properties = {
                "resilience4j.retry.instances.pgRetry.max-attempts=1",
                "resilience4j.circuitbreaker.instances.pgCircuit.sliding-window-size=10",
                "resilience4j.circuitbreaker.instances.pgCircuit.minimum-number-of-calls=3",
                "resilience4j.circuitbreaker.instances.pgCircuit.failure-rate-threshold=50",
                "resilience4j.circuitbreaker.instances.pgCircuit.wait-duration-in-open-state=1s"
        })
class PaymentFacadeCircuitBreakerBadRequestOpenIntegrationTest {

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

    private OrderModel createOrderedOrder(String suffix) {
        Long brandId = brandService.registerBrand("cb-400-brand-" + suffix).getId();
        ProductModel product = productService.registerProduct(
                brandId, "cb-400-p-" + suffix, new BigDecimal("3500"), 5);
        return orderService.create(USER_ID, List.of(
                new ProductValidationRequest(product.getId(), Quantity.of(1), null)));
    }

    @Test
    @DisplayName("Feign 400이 주문별로 연속되면 서킷이 OPEN 되고 이후 PG 호출이 스킵된다.")
    void requestPayment_whenPgReturns400Repeatedly_shouldOpenCircuitAndSkipFurtherPgCalls() {
        // given
        Request feignRequest = Request.create(
                Request.HttpMethod.POST,
                "/api/v1/payments",
                Collections.emptyMap(),
                null,
                StandardCharsets.UTF_8);
        doThrow(new FeignException.BadRequest("bad", feignRequest, null, null))
                .when(pgSimulatorClient).requestPayment(any(PgSimulatorRequest.class));

        OrderModel o1 = createOrderedOrder("1");
        OrderModel o2 = createOrderedOrder("2");
        OrderModel o3 = createOrderedOrder("3");
        OrderModel o4 = createOrderedOrder("4");

        // when
        paymentFacade.requestPayment(USER_ID, o1.getId(), "SAMSUNG", "1");
        paymentFacade.requestPayment(USER_ID, o2.getId(), "SAMSUNG", "1");
        paymentFacade.requestPayment(USER_ID, o3.getId(), "SAMSUNG", "1");
        paymentFacade.requestPayment(USER_ID, o4.getId(), "SAMSUNG", "1");

        // then
        assertThat(circuitBreakerRegistry.circuitBreaker("pgCircuit").getState())
                .isEqualTo(io.github.resilience4j.circuitbreaker.CircuitBreaker.State.OPEN);
        verify(pgSimulatorClient, times(3)).requestPayment(any(PgSimulatorRequest.class));
    }
}
