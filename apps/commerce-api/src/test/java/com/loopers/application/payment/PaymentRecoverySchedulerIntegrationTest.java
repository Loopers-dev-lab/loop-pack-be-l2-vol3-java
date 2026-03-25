package com.loopers.application.payment;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.payment.PgClient;
import com.loopers.domain.payment.PgPaymentStatusResponse;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@SpringBootTest
public class PaymentRecoverySchedulerIntegrationTest {

    private static final Long USER_ID = 1L;
    private static final Money PRODUCT_PRICE = new Money(10000);
    private static final int INITIAL_STOCK = 10;
    private static final int ORDER_QUANTITY = 2;
    private static final String CARD_TYPE = "SAMSUNG";
    private static final String CARD_NO = "4111-1111-1111-1111";
    private static final String PG_TRANSACTION_KEY = "20260319:TR:abc123";

    @Autowired
    private PaymentRecoveryScheduler scheduler;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private OrderJpaRepository orderJpaRepository;

    @Autowired
    private PaymentJpaRepository paymentJpaRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

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

    // createdAt을 과거로 설정한 Payment (폴링 대상으로 만들기 위해)
    private Payment savedOldPaymentWithTransactionKey(Long orderId, int ageSeconds) {
        Payment payment = new Payment(orderId, USER_ID,
                PRODUCT_PRICE.getAmount() * ORDER_QUANTITY, CARD_TYPE, CARD_NO);
        payment.assignTransactionKey(PG_TRANSACTION_KEY);
        Payment saved = paymentJpaRepository.save(payment);
        // createdAt을 과거로 업데이트 (폴링 threshold 통과시키기 위해)
        jdbcTemplate.update(
                "UPDATE payments SET created_at = DATE_SUB(NOW(6), INTERVAL ? SECOND) WHERE id = ?",
                ageSeconds, saved.getId());
        return paymentJpaRepository.findById(saved.getId()).get();
    }

    @Nested
    @DisplayName("미처리 결제 복구")
    class RecoverPendingPayments {

        @Test
        @DisplayName("PG 상태가 SUCCESS이면 결제를 성공 처리한다")
        void success_pgStatusSuccess() {
            // arrange
            Brand brand = savedBrand();
            Product product = savedProduct(brand.getId());
            Order order = savedOrder(USER_ID, product);
            Payment payment = savedOldPaymentWithTransactionKey(order.getId(), 60);

            given(pgClient.getPaymentStatus(USER_ID, PG_TRANSACTION_KEY))
                    .willReturn(new PgPaymentStatusResponse(PG_TRANSACTION_KEY, "SUCCESS", null));

            // act
            scheduler.recoverPendingPayments();

            // assert
            Payment updated = paymentJpaRepository.findById(payment.getId()).get();
            assertThat(updated.getStatus()).isEqualTo(PaymentStatus.SUCCESS);

            Order updatedOrder = orderJpaRepository.findById(order.getId()).get();
            assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.PAID);
        }

        @Test
        @DisplayName("PG 상태가 FAILED이면 결제를 실패 처리하고 재고를 복구한다")
        void success_pgStatusFailed_restoresStock() {
            // arrange
            Brand brand = savedBrand();
            Product product = savedProduct(brand.getId());
            product.decreaseStock(new Quantity(ORDER_QUANTITY));
            productJpaRepository.save(product);

            Order order = savedOrder(USER_ID, product);
            Payment payment = savedOldPaymentWithTransactionKey(order.getId(), 60);

            given(pgClient.getPaymentStatus(USER_ID, PG_TRANSACTION_KEY))
                    .willReturn(new PgPaymentStatusResponse(PG_TRANSACTION_KEY, "FAILED", "잔액 부족"));

            // act
            scheduler.recoverPendingPayments();

            // assert
            Payment updated = paymentJpaRepository.findById(payment.getId()).get();
            assertThat(updated.getStatus()).isEqualTo(PaymentStatus.FAILED);

            Product updatedProduct = productJpaRepository.findById(product.getId()).get();
            assertThat(updatedProduct.getStock().getQuantity()).isEqualTo(INITIAL_STOCK);
        }

        @Test
        @DisplayName("PG 상태가 PENDING이면 아무 처리도 하지 않는다 (다음 주기 대기)")
        void skip_pgStatusPending() {
            // arrange
            Brand brand = savedBrand();
            Product product = savedProduct(brand.getId());
            Order order = savedOrder(USER_ID, product);
            Payment payment = savedOldPaymentWithTransactionKey(order.getId(), 60);

            given(pgClient.getPaymentStatus(USER_ID, PG_TRANSACTION_KEY))
                    .willReturn(new PgPaymentStatusResponse(PG_TRANSACTION_KEY, "PENDING", null));

            // act
            scheduler.recoverPendingPayments();

            // assert — 상태 변화 없음
            Payment updated = paymentJpaRepository.findById(payment.getId()).get();
            assertThat(updated.getStatus()).isEqualTo(PaymentStatus.PENDING);
        }

        @Test
        @DisplayName("5분 이상 경과 + PG 상태 PENDING이면 최종 타임아웃 처리한다")
        void timeout_expired_afterPgCheck() {
            // arrange
            Brand brand = savedBrand();
            Product product = savedProduct(brand.getId());
            product.decreaseStock(new Quantity(ORDER_QUANTITY));
            productJpaRepository.save(product);

            Order order = savedOrder(USER_ID, product);
            Payment payment = savedOldPaymentWithTransactionKey(order.getId(), 360);

            // PG 조회 시 아직 PENDING (타임아웃 전 최종 확인에서도 미완료)
            given(pgClient.getPaymentStatus(USER_ID, PG_TRANSACTION_KEY))
                    .willReturn(new PgPaymentStatusResponse(PG_TRANSACTION_KEY, "PENDING", null));

            // act
            scheduler.recoverPendingPayments();

            // assert
            Payment updated = paymentJpaRepository.findById(payment.getId()).get();
            assertThat(updated.getStatus()).isEqualTo(PaymentStatus.TIMEOUT);

            Order updatedOrder = orderJpaRepository.findById(order.getId()).get();
            assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.PAYMENT_TIMEOUT);

            Product updatedProduct = productJpaRepository.findById(product.getId()).get();
            assertThat(updatedProduct.getStock().getQuantity()).isEqualTo(INITIAL_STOCK);
        }

        @Test
        @DisplayName("5분 이상 경과했지만 PG 상태가 SUCCESS이면 성공 처리한다 (유령 결제 방지)")
        void success_expired_butPgSuccess() {
            // arrange
            Brand brand = savedBrand();
            Product product = savedProduct(brand.getId());
            Order order = savedOrder(USER_ID, product);
            Payment payment = savedOldPaymentWithTransactionKey(order.getId(), 360);

            // PG에서는 이미 결제 성공 (유령 결제 방지 시나리오)
            given(pgClient.getPaymentStatus(USER_ID, PG_TRANSACTION_KEY))
                    .willReturn(new PgPaymentStatusResponse(PG_TRANSACTION_KEY, "SUCCESS", null));

            // act
            scheduler.recoverPendingPayments();

            // assert — 5분 경과했지만 PG SUCCESS이므로 성공 처리
            Payment updated = paymentJpaRepository.findById(payment.getId()).get();
            assertThat(updated.getStatus()).isEqualTo(PaymentStatus.SUCCESS);

            Order updatedOrder = orderJpaRepository.findById(order.getId()).get();
            assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.PAID);
        }
    }
}
