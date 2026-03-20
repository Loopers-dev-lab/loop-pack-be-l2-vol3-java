package com.loopers.application.payment;

import com.loopers.domain.brand.BrandService;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductValidationRequest;
import com.loopers.domain.product.Quantity;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;

/**
 * 역할: 콜백 처리 시 도메인 호출 순서를 검증한다.
 * {@code completePayment} 진입 시점에 DB 결제 행이 아직 PENDING인지(Spy) 확인해, 잘못된 순서 전이를 막는다 (06 §14).
 */
@SpringBootTest
@Import(MySqlTestContainersConfig.class)
class PaymentFacadeCallbackOrderIntegrationTest {

    private static final long USER_ID = 1L;
    private static final String CB = "http://localhost:8080/api/v1/payments/callback";

    @Autowired
    private PaymentFacade paymentFacade;
    @Autowired
    private PaymentPersistenceService persistenceService;
    @SpyBean
    private OrderService orderService;
    @Autowired
    private BrandService brandService;
    @Autowired
    private ProductService productService;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    /** completePayment 호출 직전 스냅샷이 PENDING임을 보장(성공 콜백 플로우). */
    @Test
    @DisplayName("completePayment 진입 시점에 DB상 결제는 아직 PENDING이다 (이후 markSuccess).")
    void handleCallback_orderOfOperations_pendingFetchedBeforeCompletePayment() {
        // given
        Long brandId = brandService.registerBrand("order-op-brand").getId();
        ProductModel product = productService.registerProduct(brandId, "order-op-p", new BigDecimal("6000"), 8);
        OrderModel order = orderService.create(USER_ID, List.of(
                new ProductValidationRequest(product.getId(), Quantity.of(1), null)));
        persistenceService.savePendingAndGetRequestParam(USER_ID, order.getId(), "SAMSUNG", "1", CB);
        long amountWon = order.getFinalAmount().setScale(0, java.math.RoundingMode.HALF_UP).longValue();

        doAnswer(invocation -> {
            Long oid = invocation.getArgument(0);
            assertThat(paymentRepository.findTopByOrderIdOrderByCreatedAtDesc(oid).orElseThrow().isPending()).isTrue();
            return invocation.callRealMethod();
        }).when(orderService).completePayment(anyLong());

        // when
        paymentFacade.handleCallback(new PaymentCallbackParam(order.getId(), true, "pg-order", null, amountWon));

        // then
        assertThat(paymentRepository.findTopByOrderIdOrderByCreatedAtDesc(order.getId()).orElseThrow().isPending())
                .isFalse();
    }
}
