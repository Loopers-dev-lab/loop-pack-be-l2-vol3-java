package com.loopers.application.payment;

import com.loopers.domain.brand.BrandDomainService;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderDomainService;
import com.loopers.domain.order.OrderItemCommand;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentDomainService;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductDomainService;
import com.loopers.domain.product.Money;
import com.loopers.domain.stock.ProductStockDomainService;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("결제 콜백 동시성 테스트")
class PaymentCallbackConcurrencyIntegrationTest {

    @Autowired
    private PaymentApplicationService paymentApplicationService;

    @Autowired
    private PaymentTransactionHelper transactionHelper;

    @Autowired
    private PaymentDomainService paymentDomainService;

    @Autowired
    private OrderDomainService orderDomainService;

    @Autowired
    private ProductDomainService productDomainService;

    @Autowired
    private ProductStockDomainService productStockDomainService;

    @Autowired
    private BrandDomainService brandDomainService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private Long orderId;
    private String transactionKey;

    @BeforeEach
    void setUp() {
        // 브랜드 + 상품 + 재고 생성
        var brand = brandDomainService.register("나이키");
        Product product = productDomainService.register(brand.getId(), "에어맥스", 50000);
        productStockDomainService.create(product.getId(), 10);

        // 주문 생성 (items 포함)
        Order order = orderDomainService.createOrder(1L, List.of(
            new OrderItemCommand(product.getId(), "에어맥스", new Money(50000), "나이키", 1)
        ));
        orderId = order.getId();

        // TX1: Payment(PENDING) + Order(PAYMENT_PENDING)
        Payment payment = transactionHelper.initializePayment(orderId, 1L, CardType.SAMSUNG, "1234-5678-9012-3456");

        // TX2: Payment(IN_PROGRESS)
        transactionKey = "20250316:TR:concurrent-test";
        transactionHelper.markPaymentInProgress(orderId, 1L, transactionKey);
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("동시 중복 콜백 시, ")
    @Nested
    class ConcurrentCallback {

        @DisplayName("SUCCESS/SUCCESS 동시 콜백이면, 두 요청 모두 예외 없이 완료되고 최종 상태는 PAID이다.")
        @Test
        void bothSucceed_whenConcurrentSuccessCallbacks() throws Exception {
            int threadCount = 2;
            ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
            CountDownLatch readyLatch = new CountDownLatch(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            List<Future<?>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                futures.add(executorService.submit(() -> {
                    readyLatch.countDown();
                    try {
                        startLatch.await(); // 동시 시작 보장
                        paymentApplicationService.handleCallback(transactionKey, "SUCCESS", "정상 승인");
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        failCount.incrementAndGet();
                    }
                }));
            }

            readyLatch.await(); // 두 스레드 모두 준비 완료 대기
            startLatch.countDown(); // 동시 시작

            // 타임아웃 설정 — 락 테스트 무한 대기 방지
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
            executorService.shutdown();

            // 두 요청 모두 예외 없이 완료 (멱등 직렬화)
            assertThat(successCount.get()).isEqualTo(threadCount);
            assertThat(failCount.get()).isEqualTo(0);

            // 최종 상태 검증
            Payment payment = paymentDomainService.getByTransactionKey(transactionKey);
            Order order = orderDomainService.getById(orderId);

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        }

        @DisplayName("FAILED/FAILED 동시 콜백이면, 두 요청 모두 예외 없이 완료되고 최종 상태는 FAILED이다.")
        @Test
        void bothSucceed_whenConcurrentFailedCallbacks() throws Exception {
            int threadCount = 2;
            ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
            CountDownLatch readyLatch = new CountDownLatch(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            List<Future<?>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                futures.add(executorService.submit(() -> {
                    readyLatch.countDown();
                    try {
                        startLatch.await();
                        paymentApplicationService.handleCallback(transactionKey, "FAILED", "한도초과");
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        failCount.incrementAndGet();
                    }
                }));
            }

            readyLatch.await();
            startLatch.countDown();

            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
            executorService.shutdown();

            assertThat(successCount.get()).isEqualTo(threadCount);
            assertThat(failCount.get()).isEqualTo(0);

            Payment payment = paymentDomainService.getByTransactionKey(transactionKey);
            Order order = orderDomainService.getById(orderId);

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
        }
    }
}
