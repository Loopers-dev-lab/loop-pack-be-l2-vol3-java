package com.loopers.application.payment;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.payment.PgClient;
import com.loopers.domain.product.Money;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.Quantity;
import com.loopers.domain.product.Stock;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.order.OrderJpaRepository;
import com.loopers.infrastructure.payment.PaymentJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class PaymentResultHandlerIntegrationTest {

    private static final Long USER_ID = 1L;
    private static final Money PRODUCT_PRICE = new Money(10000);
    private static final int INITIAL_STOCK = 10;
    private static final int ORDER_QUANTITY = 2;
    private static final String CARD_TYPE = "SAMSUNG";
    private static final String CARD_NO = "4111-1111-1111-1111";
    private static final String PG_TRANSACTION_KEY = "20260319:TR:abc123";

    @Autowired
    private PaymentResultHandler resultHandler;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private OrderJpaRepository orderJpaRepository;

    @Autowired
    private PaymentJpaRepository paymentJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    // PaymentEventListener가 AFTER_COMMIT 이벤트를 처리하지 않도록 Mock 처리
    @MockitoBean
    private PgClient pgClient;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private Brand savedBrand() {
        return brandJpaRepository.save(new Brand("나이키"));
    }

    private Product savedProduct(Long brandId) {
        return productJpaRepository.save(
                new Product(brandId, "나이키 에어맥스", PRODUCT_PRICE, new Stock(INITIAL_STOCK)));
    }

    private Order savedOrder(Long userId, Product product) {
        List<OrderItem> items = List.of(
                new OrderItem(product.getId(), new Quantity(ORDER_QUANTITY),
                        product.getName(), "나이키", product.getPrice())
        );
        Order order = new Order(userId, items, null,
                new Money(PRODUCT_PRICE.getAmount() * ORDER_QUANTITY), new Money(0));
        return orderJpaRepository.save(order);
    }

    private Payment savedPayment(Long orderId) {
        Payment payment = new Payment(orderId, USER_ID,
                PRODUCT_PRICE.getAmount() * ORDER_QUANTITY, CARD_TYPE, CARD_NO);
        return paymentJpaRepository.save(payment);
    }

    // transactionKey가 할당된 상태의 Payment (PG 접수 완료 후)
    private Payment savedPaymentWithTransactionKey(Long orderId) {
        Payment payment = new Payment(orderId, USER_ID,
                PRODUCT_PRICE.getAmount() * ORDER_QUANTITY, CARD_TYPE, CARD_NO);
        payment.assignTransactionKey(PG_TRANSACTION_KEY);
        return paymentJpaRepository.save(payment);
    }

    @Nested
    @DisplayName("PG 접수 성공 처리")
    class HandlePgAccepted {

        @Test
        @DisplayName("transactionKey가 Payment에 저장된다")
        void success() {
            // arrange
            Brand brand = savedBrand();
            Product product = savedProduct(brand.getId());
            Order order = savedOrder(USER_ID, product);
            Payment payment = savedPayment(order.getId());

            // act
            resultHandler.handlePgAccepted(payment.getId(), PG_TRANSACTION_KEY);

            // assert
            Payment updatedPayment = paymentJpaRepository.findById(payment.getId()).get();
            assertThat(updatedPayment.getTransactionKey()).isEqualTo(PG_TRANSACTION_KEY);
            assertThat(updatedPayment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        }
    }

    @Nested
    @DisplayName("PG 접수 실패 처리 (서버 에러 — 접수 거부 확정)")
    class HandlePgFailed {

        @Test
        @DisplayName("Payment FAILED + Order PAYMENT_FAILED로 전이된다")
        void success_statusTransition() {
            // arrange
            Brand brand = savedBrand();
            Product product = savedProduct(brand.getId());
            Order order = savedOrder(USER_ID, product);
            Payment payment = savedPayment(order.getId());

            // act
            resultHandler.handlePgFailed(payment.getId(), order.getId(), "PG 연동 실패");

            // assert
            Payment updatedPayment = paymentJpaRepository.findById(payment.getId()).get();
            assertThat(updatedPayment.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(updatedPayment.getFailureReason()).isEqualTo("PG 연동 실패");

            Order updatedOrder = orderJpaRepository.findById(order.getId()).get();
            assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
        }

        @Test
        @DisplayName("재고가 주문 수량만큼 복구된다")
        void success_restoresStock() {
            // arrange
            Brand brand = savedBrand();
            Product product = savedProduct(brand.getId());
            product.decreaseStock(new Quantity(ORDER_QUANTITY));
            productJpaRepository.save(product);

            Order order = savedOrder(USER_ID, product);
            Payment payment = savedPayment(order.getId());

            int stockBeforeRestore = productJpaRepository.findById(product.getId()).get()
                    .getStock().getQuantity();

            // act
            resultHandler.handlePgFailed(payment.getId(), order.getId(), "PG 연동 실패");

            // assert
            Product updatedProduct = productJpaRepository.findById(product.getId()).get();
            assertThat(updatedProduct.getStock().getQuantity()).isEqualTo(stockBeforeRestore + ORDER_QUANTITY);
        }
    }

    @Nested
    @DisplayName("PG 응답 시간 초과 처리 (결과 불확실 — 폴링 대상)")
    class HandlePgResponseTimeout {

        @Test
        @DisplayName("상태를 PENDING으로 유지한다 (폴링 스케줄러가 계속 확인할 수 있도록)")
        void keepsRequestedStatus() {
            // arrange
            Brand brand = savedBrand();
            Product product = savedProduct(brand.getId());
            Order order = savedOrder(USER_ID, product);
            Payment payment = savedPayment(order.getId());

            // act
            resultHandler.handlePgResponseTimeout(payment.getId(), order.getId(), "PG 응답 시간 초과");

            // assert — 상태 변경 없음
            Payment updatedPayment = paymentJpaRepository.findById(payment.getId()).get();
            assertThat(updatedPayment.getStatus()).isEqualTo(PaymentStatus.PENDING);

            Order updatedOrder = orderJpaRepository.findById(order.getId()).get();
            assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        }

        @Test
        @DisplayName("재고를 복구하지 않는다 (PG에서 결제가 승인되었을 수 있으므로)")
        void doesNotRestoreStock() {
            // arrange
            Brand brand = savedBrand();
            Product product = savedProduct(brand.getId());
            product.decreaseStock(new Quantity(ORDER_QUANTITY));
            productJpaRepository.save(product);

            Order order = savedOrder(USER_ID, product);
            Payment payment = savedPayment(order.getId());

            int stockBeforeTimeout = productJpaRepository.findById(product.getId()).get()
                    .getStock().getQuantity();

            // act
            resultHandler.handlePgResponseTimeout(payment.getId(), order.getId(), "PG 응답 시간 초과");

            // assert — 재고 변동 없음
            Product updatedProduct = productJpaRepository.findById(product.getId()).get();
            assertThat(updatedProduct.getStock().getQuantity()).isEqualTo(stockBeforeTimeout);
        }
    }

    @Nested
    @DisplayName("최종 타임아웃 처리 (5분 경과 — 결과 확정 불가)")
    class HandleFinalTimeout {

        @Test
        @DisplayName("Payment TIMEOUT + Order PAYMENT_TIMEOUT으로 전이된다")
        void success_statusTransition() {
            // arrange
            Brand brand = savedBrand();
            Product product = savedProduct(brand.getId());
            Order order = savedOrder(USER_ID, product);
            Payment payment = savedPayment(order.getId());

            // act
            resultHandler.handleFinalTimeout(payment.getId(), order.getId(), "결제 최종 타임아웃");

            // assert
            Payment updatedPayment = paymentJpaRepository.findById(payment.getId()).get();
            assertThat(updatedPayment.getStatus()).isEqualTo(PaymentStatus.TIMEOUT);
            assertThat(updatedPayment.getFailureReason()).isEqualTo("결제 응답 시간 초과");

            Order updatedOrder = orderJpaRepository.findById(order.getId()).get();
            assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.PAYMENT_TIMEOUT);
        }

        @Test
        @DisplayName("재고가 주문 수량만큼 복구된다")
        void success_restoresStock() {
            // arrange
            Brand brand = savedBrand();
            Product product = savedProduct(brand.getId());
            product.decreaseStock(new Quantity(ORDER_QUANTITY));
            productJpaRepository.save(product);

            Order order = savedOrder(USER_ID, product);
            Payment payment = savedPayment(order.getId());

            int stockBeforeRestore = productJpaRepository.findById(product.getId()).get()
                    .getStock().getQuantity();

            // act
            resultHandler.handleFinalTimeout(payment.getId(), order.getId(), "결제 최종 타임아웃");

            // assert
            Product updatedProduct = productJpaRepository.findById(product.getId()).get();
            assertThat(updatedProduct.getStock().getQuantity()).isEqualTo(stockBeforeRestore + ORDER_QUANTITY);
        }
    }

    @Nested
    @DisplayName("콜백 처리")
    class HandleCallback {

        @Test
        @DisplayName("SUCCESS 콜백: Payment SUCCESS + Order PAID로 전이된다")
        void success_callback() {
            // arrange
            Brand brand = savedBrand();
            Product product = savedProduct(brand.getId());
            Order order = savedOrder(USER_ID, product);
            Payment payment = savedPaymentWithTransactionKey(order.getId());

            // act
            boolean result = resultHandler.handleCallback(PG_TRANSACTION_KEY, "SUCCESS", null);

            // assert
            assertThat(result).isTrue();

            Payment updatedPayment = paymentJpaRepository.findById(payment.getId()).get();
            assertThat(updatedPayment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
            assertThat(updatedPayment.getPgRespondedAt()).isNotNull();

            Order updatedOrder = orderJpaRepository.findById(order.getId()).get();
            assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.PAID);
        }

        @Test
        @DisplayName("FAILED 콜백: Payment FAILED + Order PAYMENT_FAILED로 전이되고 재고가 복구된다")
        void failed_callback_restoresStock() {
            // arrange
            Brand brand = savedBrand();
            Product product = savedProduct(brand.getId());
            // 재고 차감 시뮬레이션
            product.decreaseStock(new Quantity(ORDER_QUANTITY));
            productJpaRepository.save(product);

            Order order = savedOrder(USER_ID, product);
            Payment payment = savedPaymentWithTransactionKey(order.getId());

            // act
            boolean result = resultHandler.handleCallback(PG_TRANSACTION_KEY, "FAILED", "잔액 부족");

            // assert
            assertThat(result).isTrue();

            Payment updatedPayment = paymentJpaRepository.findById(payment.getId()).get();
            assertThat(updatedPayment.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(updatedPayment.getFailureReason()).isEqualTo("잔액 부족");

            Order updatedOrder = orderJpaRepository.findById(order.getId()).get();
            assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);

            Product updatedProduct = productJpaRepository.findById(product.getId()).get();
            assertThat(updatedProduct.getStock().getQuantity()).isEqualTo(INITIAL_STOCK);
        }

        @Test
        @DisplayName("이미 처리된 결제에 중복 콜백이 오면 무시한다 (멱등성)")
        void idempotent_duplicateCallback() {
            // arrange
            Brand brand = savedBrand();
            Product product = savedProduct(brand.getId());
            Order order = savedOrder(USER_ID, product);
            Payment payment = savedPaymentWithTransactionKey(order.getId());

            // 첫 번째 콜백 — 성공 처리
            resultHandler.handleCallback(PG_TRANSACTION_KEY, "SUCCESS", null);

            // act — 동일한 콜백 재수신
            boolean result = resultHandler.handleCallback(PG_TRANSACTION_KEY, "SUCCESS", null);

            // assert — 무시됨 (false 반환)
            assertThat(result).isFalse();

            // 상태는 그대로 SUCCESS
            Payment updatedPayment = paymentJpaRepository.findById(payment.getId()).get();
            assertThat(updatedPayment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        }
    }
}
