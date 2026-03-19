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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 동시성 테스트: 콜백 + 폴링이 동시에 같은 Payment를 처리하는 시나리오.
 *
 * WHERE status='PENDING' 조건부 UPDATE가 @Version 낙관적 락 없이도
 * 중복 처리를 방어하는지 검증한다.
 */
@SpringBootTest
public class PaymentCallbackConcurrencyTest {

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

    // 실제 PG 이벤트 리스너가 발동하지 않도록 Mock
    @MockitoBean
    private PgClient pgClient;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private Payment savedPaymentWithTransactionKey() {
        Brand brand = brandJpaRepository.save(new Brand("나이키"));
        Product product = productJpaRepository.save(
                new Product(brand.getId(), "나이키 에어맥스", PRODUCT_PRICE, new Stock(INITIAL_STOCK)));

        List<OrderItem> items = List.of(
                new OrderItem(product.getId(), new Quantity(ORDER_QUANTITY),
                        product.getName(), "나이키", product.getPrice())
        );
        Order order = orderJpaRepository.save(new Order(USER_ID, items, null,
                new Money(PRODUCT_PRICE.getAmount() * ORDER_QUANTITY), new Money(0)));

        Payment payment = new Payment(order.getId(), USER_ID,
                PRODUCT_PRICE.getAmount() * ORDER_QUANTITY, CARD_TYPE, CARD_NO);
        payment.assignTransactionKey(PG_TRANSACTION_KEY);
        return paymentJpaRepository.save(payment);
    }

    @Nested
    @DisplayName("SUCCESS 콜백 중복 수신")
    class DuplicateSuccessCallback {

        @Test
        @DisplayName("동일 SUCCESS 콜백이 동시에 2회 수신되어도 정확히 1회만 처리된다")
        void handleCallback_concurrent_idempotency() throws InterruptedException {
            // arrange
            Payment payment = savedPaymentWithTransactionKey();

            int threadCount = 2;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await(); // 모든 스레드가 동시에 출발
                        boolean result = resultHandler.handleCallback(PG_TRANSACTION_KEY, "SUCCESS", null);
                        if (result) {
                            successCount.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            // act
            startLatch.countDown(); // 동시 시작
            doneLatch.await();
            executor.shutdown();

            // assert — WHERE status='PENDING' 조건부 UPDATE로 정확히 1건만 처리
            assertThat(successCount.get()).isEqualTo(1);

            Payment updatedPayment = paymentJpaRepository.findById(payment.getId()).get();
            assertThat(updatedPayment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        }
    }

    @Nested
    @DisplayName("콜백 + 최종 타임아웃 동시 처리")
    class CallbackAndFinalTimeoutRace {

        @Test
        @DisplayName("SUCCESS 콜백과 최종 타임아웃이 동시에 도달하면 먼저 처리된 쪽만 반영된다")
        void callback_and_finalTimeout_concurrent() throws InterruptedException {
            // arrange
            Payment payment = savedPaymentWithTransactionKey();
            Long orderId = payment.getOrderId();

            int threadCount = 2;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            // Thread 1: SUCCESS 콜백 처리
            executor.submit(() -> {
                try {
                    startLatch.await();
                    boolean result = resultHandler.handleCallback(PG_TRANSACTION_KEY, "SUCCESS", null);
                    if (result) successCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });

            // Thread 2: 최종 타임아웃 처리 (폴링 스케줄러가 동시에 실행되는 상황)
            executor.submit(() -> {
                try {
                    startLatch.await();
                    resultHandler.handleFinalTimeout(payment.getId(), orderId, "5분 초과");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });

            // act
            startLatch.countDown();
            doneLatch.await();
            executor.shutdown();

            // assert — 둘 중 하나만 최종 상태로 전이됨 (SUCCESS 또는 TIMEOUT, 둘 다는 불가)
            Payment updatedPayment = paymentJpaRepository.findById(payment.getId()).get();
            assertThat(updatedPayment.getStatus()).isIn(PaymentStatus.SUCCESS, PaymentStatus.TIMEOUT);

            // Order 상태도 Payment 상태와 일관성 유지
            Order updatedOrder = orderJpaRepository.findById(orderId).get();
            if (updatedPayment.getStatus() == PaymentStatus.SUCCESS) {
                assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.PAID);
            } else {
                assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.PAYMENT_TIMEOUT);
            }
        }
    }
}
