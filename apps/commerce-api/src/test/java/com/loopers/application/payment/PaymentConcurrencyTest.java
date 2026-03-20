package com.loopers.application.payment;

import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderItemRepository;
import com.loopers.domain.payment.GatewayPaymentResult;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.product.ProductStockModel;
import com.loopers.domain.product.StockService;
import com.loopers.support.enums.CardType;
import com.loopers.support.enums.OrderType;
import com.loopers.support.enums.PaymentStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("결제 동시성 통합 테스트 (Redis 분산락)")
class PaymentConcurrencyTest {

    @Autowired PaymentFacade paymentFacade;
    @Autowired OrderRepository orderRepository;
    @Autowired OrderItemRepository orderItemRepository;
    @Autowired StockService stockService;
    @Autowired PaymentRepository paymentRepository;
    @Autowired DatabaseCleanUp databaseCleanUp;
    @Autowired RedisCleanUp redisCleanUp;

    @MockitoBean PaymentGateway paymentGateway;

    private static final Long USER_ID = 1L;
    private static final CardType CARD_TYPE = CardType.SAMSUNG;
    private static final String CARD_NO = "1234-5678-9012-3456";

    private Long orderId;
    private Long productId;

    @BeforeEach
    void setUp() {
        // 재고 생성
        ProductStockModel stock = stockService.createStock(1L, 100);
        productId = stock.getProductId();

        // 주문 생성
        OrderModel order = orderRepository.save(
                OrderModel.create(USER_ID, OrderType.DIRECT, BigDecimal.valueOf(10000)));
        orderId = order.getOrderId();

        // 주문 항목 생성
        orderItemRepository.save(
                OrderItemModel.create(orderId, 1, USER_ID, productId, 2,
                        "테스트상품", BigDecimal.valueOf(5000),
                        "brand-1", "테스트브랜드", null));

        // PG Mock: 약간의 지연 후 성공 응답
        when(paymentGateway.requestPayment(eq(orderId), eq(USER_ID), eq(CARD_TYPE), eq(CARD_NO), any(), any()))
                .thenAnswer(invocation -> {
                    Thread.sleep(50); // PG 응답 지연 시뮬레이션
                    return new GatewayPaymentResult("txn-" + System.nanoTime(), true, "PENDING", null);
                });
    }

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("같은 주문에 2개 스레드가 동시 결제 시 1건만 PG 호출된다")
    void concurrentPayment_SameOrder_ShouldOnlyOneSucceed() throws InterruptedException {
        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger alreadyInProgressCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    paymentFacade.requestPayment(USER_ID, orderId, CARD_TYPE, CARD_NO);
                    successCount.incrementAndGet();
                } catch (CoreException e) {
                    if (e.getErrorType() == ErrorType.PAYMENT_ALREADY_IN_PROGRESS) {
                        alreadyInProgressCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        // 1건만 성공, 1건은 PAYMENT_ALREADY_IN_PROGRESS
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(alreadyInProgressCount.get()).isEqualTo(1);

        // PG 호출도 정확히 1회
        verify(paymentGateway, times(1))
                .requestPayment(eq(orderId), eq(USER_ID), eq(CARD_TYPE), eq(CARD_NO), any(), any());

        // Payment 레코드도 1건만 생성
        assertThat(paymentRepository.findAllByOrderId(orderId)).hasSize(1);
    }

    @Test
    @DisplayName("같은 주문에 5개 스레드가 동시 결제 시 1건만 성공하고 나머지는 차단된다")
    void concurrentPayment_FiveThreads_ShouldOnlyOneSucceed() throws InterruptedException {
        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger alreadyInProgressCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    paymentFacade.requestPayment(USER_ID, orderId, CARD_TYPE, CARD_NO);
                    successCount.incrementAndGet();
                } catch (CoreException e) {
                    if (e.getErrorType() == ErrorType.PAYMENT_ALREADY_IN_PROGRESS) {
                        alreadyInProgressCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(alreadyInProgressCount.get()).isEqualTo(threadCount - 1);

        verify(paymentGateway, times(1))
                .requestPayment(eq(orderId), eq(USER_ID), eq(CARD_TYPE), eq(CARD_NO), any(), any());
    }

    @Test
    @DisplayName("서로 다른 주문은 동시 결제가 각각 성공한다")
    void concurrentPayment_DifferentOrders_ShouldAllSucceed() throws InterruptedException {
        // 두 번째 주문 생성
        OrderModel order2 = orderRepository.save(
                OrderModel.create(USER_ID, OrderType.DIRECT, BigDecimal.valueOf(10000)));
        Long orderId2 = order2.getOrderId();
        orderItemRepository.save(
                OrderItemModel.create(orderId2, 1, USER_ID, productId, 1,
                        "테스트상품", BigDecimal.valueOf(5000),
                        "brand-1", "테스트브랜드", null));

        // 두 번째 주문에 대한 PG mock 추가
        when(paymentGateway.requestPayment(eq(orderId2), eq(USER_ID), eq(CARD_TYPE), eq(CARD_NO), any(), any()))
                .thenAnswer(invocation -> {
                    Thread.sleep(50);
                    return new GatewayPaymentResult("txn-" + System.nanoTime(), true, "PENDING", null);
                });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);
        AtomicInteger successCount = new AtomicInteger(0);

        // 주문1 결제
        executor.submit(() -> {
            try {
                startLatch.await();
                paymentFacade.requestPayment(USER_ID, orderId, CARD_TYPE, CARD_NO);
                successCount.incrementAndGet();
            } catch (Exception e) {
                // 실패
            } finally {
                doneLatch.countDown();
            }
        });

        // 주문2 결제
        executor.submit(() -> {
            try {
                startLatch.await();
                paymentFacade.requestPayment(USER_ID, orderId2, CARD_TYPE, CARD_NO);
                successCount.incrementAndGet();
            } catch (Exception e) {
                // 실패
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        // 서로 다른 주문이므로 둘 다 성공
        assertThat(successCount.get()).isEqualTo(2);
    }
}
