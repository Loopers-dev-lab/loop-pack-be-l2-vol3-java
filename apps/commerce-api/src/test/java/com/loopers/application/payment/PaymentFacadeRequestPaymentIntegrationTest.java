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
import org.mockito.stubbing.Answer;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * PG 호출 실패 시에도 PENDING 유지·예외 삼킴 (06-payment-change-issues §3.1).
 */
@SpringBootTest
@Import(MySqlTestContainersConfig.class)
class PaymentFacadeRequestPaymentIntegrationTest {

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

    @MockBean
    private PgSimulatorClient pgSimulatorClient;

    @AfterEach
    void tearDown() {
        // CircuitBreaker 상태가 다른 테스트에 누수되지 않도록 초기화한다.
        circuitBreakerRegistry.circuitBreaker("pgCircuit").transitionToClosedState();
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("PG 호출이 예외를 던져도 PENDING 정보를 반환하고 DB에 PENDING이 남는다.")
    void requestPayment_whenPgThrows_shouldStillReturnPendingInfo() {
        // given
        doThrow(new RuntimeException("simulated pg failure"))
                .when(pgSimulatorClient).requestPayment(any(PgSimulatorRequest.class));

        Long brandId = brandService.registerBrand("req-brand").getId();
        ProductModel product = productService.registerProduct(brandId, "r", new BigDecimal("8000"), 5);
        OrderModel order = orderService.create(1L, List.of(
                new ProductValidationRequest(product.getId(), Quantity.of(1), null)));

        // when
        PaymentInfo info = paymentFacade.requestPayment(1L, order.getId(), "SAMSUNG", "1111");

        // then
        assertThat(info.status()).isEqualTo("PENDING");
        assertThat(info.orderId()).isEqualTo(order.getId());
        assertThat(paymentRepository.existsByOrderIdAndStatus(order.getId(), PaymentStatus.PENDING)).isTrue();
        verify(pgSimulatorClient, times(3)).requestPayment(any(PgSimulatorRequest.class));
    }

    @Test
    @DisplayName("PENDING 커밋 이후 PG 호출 시점에는 활성 트랜잭션이 없다.")
    void requestPayment_afterPersistenceCommit_callsPgClientOutsideTx() {
        // given
        Answer<PgSimulatorResponse> assertNoActiveTx = invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return new PgSimulatorResponse("tx-outside");
        };
        doAnswer(assertNoActiveTx).when(pgSimulatorClient).requestPayment(any(PgSimulatorRequest.class));

        Long brandId = brandService.registerBrand("tx-boundary-brand").getId();
        ProductModel product = productService.registerProduct(brandId, "t", new BigDecimal("7000"), 4);
        OrderModel order = orderService.create(1L, List.of(
                new ProductValidationRequest(product.getId(), Quantity.of(1), null)));

        // when
        paymentFacade.requestPayment(1L, order.getId(), "SAMSUNG", "2222");

        // then
        verify(pgSimulatorClient, times(1)).requestPayment(any(PgSimulatorRequest.class));
    }
}
