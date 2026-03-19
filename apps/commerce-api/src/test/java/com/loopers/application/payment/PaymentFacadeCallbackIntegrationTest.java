package com.loopers.application.payment;

import com.loopers.domain.brand.BrandService;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductValidationRequest;
import com.loopers.domain.product.Quantity;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 콜백 처리·금액 대조·멱등 (06-payment-change-issues §3.2, §4.2, §7).
 */
@SpringBootTest
@Import(MySqlTestContainersConfig.class)
class PaymentFacadeCallbackIntegrationTest {

    private static final Long USER_ID = 1L;
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

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private record OrderAndProduct(OrderModel order, Long productId) {
    }

    private OrderAndProduct createOrderedOrderWithStock(int stock) {
        Long brandId = brandService.registerBrand("cb-brand").getId();
        ProductModel product = productService.registerProduct(brandId, "cb-prod", new BigDecimal("10000"), stock);
        OrderModel order = orderService.create(USER_ID, List.of(
                new ProductValidationRequest(product.getId(), Quantity.of(1), null)));
        return new OrderAndProduct(order, product.getId());
    }

    private long expectedAmountWon(OrderModel order) {
        return order.getFinalAmount().setScale(0, java.math.RoundingMode.HALF_UP).longValue();
    }

    @Nested
    @DisplayName("handleCallback 시")
    class HandleCallback {

        @Test
        @DisplayName("성공 콜백이면 주문 PAID·재고 차감·결제 SUCCESS다.")
        void handleCallback_whenSuccess_shouldMarkPaidAndDecreaseStock() {
            // given
            OrderAndProduct ctx = createOrderedOrderWithStock(10);
            persistenceService.savePendingAndGetRequestParam(
                    USER_ID, ctx.order().getId(), "SAMSUNG", "1", CB);
            long amount = expectedAmountWon(ctx.order());

            // when
            paymentFacade.handleCallback(new PaymentCallbackParam(
                    ctx.order().getId(), true, "pg-tx-1", null, amount));

            // then
            OrderModel after = orderService.findById(USER_ID, ctx.order().getId()).orElseThrow();
            assertThat(after.getStatus()).isEqualTo(OrderStatus.PAID);
            assertThat(productService.findById(ctx.productId()).orElseThrow().getStockQuantity()).isEqualTo(9);
            var pay = paymentRepository.findTopByOrderIdOrderByCreatedAtDesc(ctx.order().getId()).orElseThrow();
            assertThat(pay.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
            assertThat(pay.getPgTransactionId()).isEqualTo("pg-tx-1");
        }

        @Test
        @DisplayName("실패 콜백이면 결제 FAILED이고 주문은 ORDERED다.")
        void handleCallback_whenFailure_shouldMarkFailedAndOrderStaysORDERED() {
            // given
            OrderAndProduct ctx = createOrderedOrderWithStock(10);
            persistenceService.savePendingAndGetRequestParam(
                    USER_ID, ctx.order().getId(), "SAMSUNG", "1", CB);

            // when
            paymentFacade.handleCallback(new PaymentCallbackParam(
                    ctx.order().getId(), false, null, "LIMIT_EXCEEDED", null));

            // then
            OrderModel after = orderService.findById(USER_ID, ctx.order().getId()).orElseThrow();
            assertThat(after.getStatus()).isEqualTo(OrderStatus.ORDERED);
            assertThat(productService.findById(ctx.productId()).orElseThrow().getStockQuantity()).isEqualTo(10);
            var pay = paymentRepository.findTopByOrderIdOrderByCreatedAtDesc(ctx.order().getId()).orElseThrow();
            assertThat(pay.getStatus()).isEqualTo(PaymentStatus.FAILED);
        }

        @Test
        @DisplayName("콜백 금액이 주문과 다르면 completePayment를 하지 않고 결제는 PENDING이다.")
        void handleCallback_whenAmountMismatch_shouldNotCompletePayment() {
            // given
            OrderAndProduct ctx = createOrderedOrderWithStock(10);
            persistenceService.savePendingAndGetRequestParam(
                    USER_ID, ctx.order().getId(), "SAMSUNG", "1", CB);

            // when
            paymentFacade.handleCallback(new PaymentCallbackParam(
                    ctx.order().getId(), true, "pg-tx", null, 1L));

            // then
            OrderModel after = orderService.findById(USER_ID, ctx.order().getId()).orElseThrow();
            assertThat(after.getStatus()).isEqualTo(OrderStatus.ORDERED);
            assertThat(productService.findById(ctx.productId()).orElseThrow().getStockQuantity()).isEqualTo(10);
            var pay = paymentRepository.findTopByOrderIdOrderByCreatedAtDesc(ctx.order().getId()).orElseThrow();
            assertThat(pay.getStatus()).isEqualTo(PaymentStatus.PENDING);
        }

        @Test
        @DisplayName("성공 콜백을 두 번내도 재고는 한 번만 차감된다.")
        void handleCallback_whenSuccessTwice_shouldBeIdempotent() {
            // given
            OrderAndProduct ctx = createOrderedOrderWithStock(10);
            persistenceService.savePendingAndGetRequestParam(
                    USER_ID, ctx.order().getId(), "SAMSUNG", "1", CB);
            long amount = expectedAmountWon(ctx.order());
            Long orderId = ctx.order().getId();

            // when
            paymentFacade.handleCallback(new PaymentCallbackParam(orderId, true, "pg-1", null, amount));
            paymentFacade.handleCallback(new PaymentCallbackParam(orderId, true, "pg-1", null, amount));

            // then
            assertThat(productService.findById(ctx.productId()).orElseThrow().getStockQuantity()).isEqualTo(9);
            Optional<PaymentModel> latest =
                    paymentRepository.findTopByOrderIdOrderByCreatedAtDesc(orderId);
            assertThat(latest).isPresent();
            assertThat(latest.get().getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        }

        @Test
        @DisplayName("PENDING 결제가 없으면 멱등으로 아무 것도 하지 않는다.")
        void handleCallback_whenNoPending_shouldReturnSilently() {
            // given
            OrderAndProduct ctx = createOrderedOrderWithStock(10);
            Long orderId = ctx.order().getId();

            // when
            paymentFacade.handleCallback(new PaymentCallbackParam(orderId, true, "x", null, null));

            // then
            OrderModel after = orderService.findById(USER_ID, orderId).orElseThrow();
            assertThat(after.getStatus()).isEqualTo(OrderStatus.ORDERED);
            assertThat(paymentRepository.findTopByOrderIdOrderByCreatedAtDesc(orderId)).isEmpty();
        }
    }
}
