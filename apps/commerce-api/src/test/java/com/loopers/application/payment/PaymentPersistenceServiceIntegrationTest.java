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
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 역할: {@link PaymentPersistenceService#savePendingAndGetRequestParam}가 주문 상태·락·금액 검증을 통과할 때만
 * PENDING 행을 남기는지, 실패 시 {@link com.loopers.support.error.ErrorType}별로 떨어지는지 검증한다 (06 §2.1, §7).
 */
@SpringBootTest
@Import(MySqlTestContainersConfig.class)
class PaymentPersistenceServiceIntegrationTest {

    private static final Long USER_ID = 1L;
    private static final String CB = "http://localhost:8080/api/v1/payments/callback";

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

    private OrderModel createOrderedOrderForUser(Long userId) {
        Long brandId = brandService.registerBrand("pay-persist-brand").getId();
        ProductModel product = productService.registerProduct(brandId, "pay-item", new BigDecimal("10000"), 10);
        return orderService.create(userId, List.of(
                new ProductValidationRequest(product.getId(), Quantity.of(1), null)));
    }

    @Nested
    @DisplayName("savePendingAndGetRequestParam 시")
    class SavePending {

        @Test
        @DisplayName("ORDERED 주문이면 PENDING을 저장하고 주문 금액이 PG 파라미터에 반영된다.")
        void savePending_withOrderORDERED_shouldPersistAndReturnParam() {
            // given
            OrderModel order = createOrderedOrderForUser(USER_ID);

            // when
            PendingPaymentResult result = persistenceService.savePendingAndGetRequestParam(
                    USER_ID, order.getId(), "SAMSUNG", "1234-5678", CB);

            // then
            assertThat(result.paymentInfo().status()).isEqualTo("PENDING");
            assertThat(result.paymentInfo().orderId()).isEqualTo(order.getId());
            assertThat(result.requestParam().amount()).isEqualTo(10000L);
            assertThat(result.requestParam().callbackUrl()).isEqualTo(CB);
            assertThat(paymentRepository.existsByOrderIdAndStatus(order.getId(), PaymentStatus.PENDING)).isTrue();
        }

        @Test
        @DisplayName("타인 주문이면 NOT_FOUND를 던진다.")
        void savePending_withWrongUser_shouldThrowNOT_FOUND() {
            // given
            OrderModel order = createOrderedOrderForUser(2L);

            // when / then
            CoreException ex = assertThrows(CoreException.class,
                    () -> persistenceService.savePendingAndGetRequestParam(
                            USER_ID, order.getId(), "SAMSUNG", "1", CB));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @Test
        @DisplayName("주문이 ORDERED가 아니면 BAD_REQUEST를 던진다.")
        void savePending_whenOrderNotORDERED_shouldThrowBAD_REQUEST() {
            // given
            OrderModel order = createOrderedOrderForUser(USER_ID);
            orderService.cancel(USER_ID, order.getId());

            // when / then
            CoreException ex = assertThrows(CoreException.class,
                    () -> persistenceService.savePendingAndGetRequestParam(
                            USER_ID, order.getId(), "SAMSUNG", "1", CB));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @Test
        @DisplayName("이미 PENDING이 있으면 CONFLICT를 던진다.")
        void savePending_whenPENDINGAlreadyExists_shouldThrowCONFLICT() {
            // given
            OrderModel order = createOrderedOrderForUser(USER_ID);
            persistenceService.savePendingAndGetRequestParam(USER_ID, order.getId(), "SAMSUNG", "1", CB);

            // when / then
            CoreException ex = assertThrows(CoreException.class,
                    () -> persistenceService.savePendingAndGetRequestParam(
                            USER_ID, order.getId(), "SAMSUNG", "1", CB));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }
}
