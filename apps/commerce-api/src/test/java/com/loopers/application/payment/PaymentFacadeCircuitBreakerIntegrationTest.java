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
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Phase 4: Circuit Breaker OPEN 시 PG 호출 스킵·PENDING 유지·중복 요청 CONFLICT (06 §14).
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

    @Test
    @DisplayName("CircuitBreaker가 CLOSED이면 PG 호출이 1회 수행된다.")
    void requestPayment_whenCircuitBreakerClosed_shouldCallPgOnce() {
        // given
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("pgCircuit");
        circuitBreaker.transitionToClosedState();
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        when(pgSimulatorClient.requestPayment(any(PgSimulatorRequest.class)))
                .thenReturn(new PgSimulatorResponse("phase4-closed-tx"));

        Long brandId = brandService.registerBrand("cb-phase4-closed-brand").getId();
        ProductModel product = productService.registerProduct(
                brandId, "cb-phase4-closed-product", new BigDecimal("10000"), 10);
        OrderModel order = orderService.create(USER_ID, List.of(
                new ProductValidationRequest(product.getId(), Quantity.of(1), null)));

        // when
        PaymentInfo info = paymentFacade.requestPayment(USER_ID, order.getId(), "SAMSUNG", "1111");

        // then
        assertThat(info.status()).isEqualTo("PENDING");
        verify(pgSimulatorClient, times(1)).requestPayment(any(PgSimulatorRequest.class));
    }

    @Test
    @DisplayName("CircuitBreaker OPEN 중 동일 주문 재요청은 CONFLICT로 차단된다.")
    void requestPayment_whenCircuitBreakerOpenAndDuplicateOrder_shouldThrowConflict() {
        // given
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("pgCircuit");
        circuitBreaker.transitionToOpenState();
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        Long brandId = brandService.registerBrand("cb-phase4-dup-brand").getId();
        ProductModel product = productService.registerProduct(
                brandId, "cb-phase4-dup-product", new BigDecimal("10000"), 10);
        OrderModel order = orderService.create(USER_ID, List.of(
                new ProductValidationRequest(product.getId(), Quantity.of(1), null)));

        paymentFacade.requestPayment(USER_ID, order.getId(), "SAMSUNG", "1111");

        // when / then
        CoreException ex = assertThrows(CoreException.class,
                () -> paymentFacade.requestPayment(USER_ID, order.getId(), "SAMSUNG", "1111"));
        assertThat(ex.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        verify(pgSimulatorClient, never()).requestPayment(any(PgSimulatorRequest.class));
    }
}

