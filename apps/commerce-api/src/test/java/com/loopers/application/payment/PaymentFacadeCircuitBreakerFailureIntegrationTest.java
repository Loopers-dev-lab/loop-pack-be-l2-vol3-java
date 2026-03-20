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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Phase 4: 연속 실패로 Circuit OPEN 후 PG 미호출 (06 checklist 리스크 매핑).
 * Retry는 maxAttempts=1로 두어 CB 실패 카운트와 주문 건수를 1:1로 맞춘다.
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
class PaymentFacadeCircuitBreakerFailureIntegrationTest {

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
        Long brandId = brandService.registerBrand("cb-fail-brand-" + suffix).getId();
        ProductModel product = productService.registerProduct(
                brandId, "cb-fail-p-" + suffix, new BigDecimal("3000"), 6);
        return orderService.create(USER_ID, List.of(
                new ProductValidationRequest(product.getId(), Quantity.of(1), null)));
    }

    @Test
    @DisplayName("PG가 연속 실패하면 서킷이 OPEN 되고 이후 주문은 PG 호출이 스킵된다.")
    void requestPayment_whenPgFailsRepeatedly_shouldOpenCircuitAndSkipFurtherPgCalls() {
        // given
        doThrow(new RuntimeException("pg always down"))
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
