package com.loopers.application.payment;

import com.loopers.domain.brand.BrandService;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductValidationRequest;
import com.loopers.domain.product.Quantity;
import com.loopers.infrastructure.payment.PgSimulatorClient;
import com.loopers.infrastructure.payment.PgSimulatorRequest;
import com.loopers.infrastructure.payment.PgSimulatorResponse;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 4: Circuit Breaker OPEN 상태에서 PG 호출이 스킵되고 "PENDING 유지"가 되는지 검증한다.
 *
 * 아직 Circuit Breaker가 실제로 PG 호출 경로에 연결되지 않았다면 이 테스트는 실패하며,
 * 이후 코드 구현으로 Green 상태를 만드는 목표 테스트다.
 */
@SpringBootTest
@Import(MySqlTestContainersConfig.class)
class PaymentFacadeCircuitBreakerIntegrationTest {

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
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @MockBean
    private PgSimulatorClient pgSimulatorClient;

    @AfterEach
    void tearDown() {
        // CircuitBreaker 상태가 다른 테스트에 누수되지 않도록 초기화한다.
        circuitBreakerRegistry.circuitBreaker("pgCircuit").transitionToClosedState();
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("CircuitBreaker가 OPEN이면 PG 호출을 스킵하고 PENDING을 반환한다.")
    void requestPayment_whenCircuitBreakerOpen_shouldSkipPgCallAndReturnPending() {
        // given
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("pgCircuit");
        circuitBreaker.transitionToOpenState();
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        when(pgSimulatorClient.requestPayment(any(PgSimulatorRequest.class)))
                .thenReturn(new PgSimulatorResponse("phase4-tx"));

        Long brandId = brandService.registerBrand("cb-phase4-brand").getId();
        ProductModel product = productService.registerProduct(
                brandId, "cb-phase4-product", new BigDecimal("10000"), 10);
        OrderModel order = orderService.create(USER_ID, List.of(
                new ProductValidationRequest(product.getId(), Quantity.of(1), null)));

        // when
        PaymentInfo info = paymentFacade.requestPayment(USER_ID, order.getId(), "SAMSUNG", "1111");

        // then
        assertThat(info.status()).isEqualTo("PENDING");
        assertThat(paymentRepository.existsByOrderIdAndStatus(order.getId(), PaymentStatus.PENDING)).isTrue();
        verify(pgSimulatorClient, never()).requestPayment(any(PgSimulatorRequest.class));
    }
}

