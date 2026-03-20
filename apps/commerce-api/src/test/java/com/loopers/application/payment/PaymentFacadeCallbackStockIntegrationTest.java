package com.loopers.application.payment;

import com.loopers.domain.brand.BrandService;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductValidationRequest;
import com.loopers.domain.product.Quantity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 역할: 결제 완료 시점(콜백)에 재고 부족이 나면 트랜잭션이 롤백되고
 * 결제는 PENDING·주문은 ORDERED로 남는지 검증한다 (요구 §11.3, 재고는 결제 완료 시 차감).
 */
@SpringBootTest
@Import(MySqlTestContainersConfig.class)
class PaymentFacadeCallbackStockIntegrationTest {

    private static final long USER_ID = 1L;
    private static final String CB = "http://localhost:8080/api/v1/payments/callback";

    @Autowired
    private PaymentFacade paymentFacade;
    @Autowired
    private PaymentPersistenceService persistenceService;
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
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    /** 주문 시점과 달리 콜백 시점에 재고가 0이 된 경우: 완료 실패·일관성 유지. */
    @Test
    @DisplayName("성공 콜백인데 재고가 부족하면 예외이고 결제는 PENDING·주문은 ORDERED다.")
    void handleCallback_whenStockInsufficientAtPaymentTime_shouldThrowAndKeepPending() {
        // given
        Long brandId = brandService.registerBrand("cb-stock-brand").getId();
        ProductModel product = productService.registerProduct(brandId, "cb-stock-p", new BigDecimal("8000"), 5);
        OrderModel order = orderService.create(USER_ID, List.of(
                new ProductValidationRequest(product.getId(), Quantity.of(2), null)));
        persistenceService.savePendingAndGetRequestParam(USER_ID, order.getId(), "SAMSUNG", "1", CB);
        jdbcTemplate.update("UPDATE product SET stock_quantity = 0 WHERE id = ?", product.getId());
        long amountWon = order.getFinalAmount().setScale(0, RoundingMode.HALF_UP).longValue();

        // when / then
        CoreException ex = assertThrows(CoreException.class,
                () -> paymentFacade.handleCallback(new PaymentCallbackParam(
                        order.getId(), true, "pg-tx", null, amountWon)));
        assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);

        OrderModel afterOrder = orderService.findById(USER_ID, order.getId()).orElseThrow();
        assertThat(afterOrder.getStatus()).isEqualTo(OrderStatus.ORDERED);
        assertThat(paymentRepository.findTopByOrderIdOrderByCreatedAtDesc(order.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.PENDING);
    }
}
