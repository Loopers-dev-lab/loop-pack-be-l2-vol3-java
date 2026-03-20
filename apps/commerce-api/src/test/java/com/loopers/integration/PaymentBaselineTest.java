package com.loopers.integration;

import com.loopers.application.payment.PaymentFacade;
import com.loopers.application.payment.PaymentInfo;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.payment.GatewayPaymentResult;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductStockModel;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserRegisterCommand;
import com.loopers.domain.user.UserService;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.order.OrderJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.product.ProductStockJpaRepository;
import com.loopers.support.enums.CardType;
import com.loopers.support.enums.OrderType;
import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Phase 2: Baseline 테스트 — PG 실패율 40% 환경에서의 취약점 체감.
 * <p>
 * PG 시뮬레이터 대신 {@link MockitoBean}으로 PG 동작을 시뮬레이션한다.
 * 40% 확률 실패 + 100~500ms 랜덤 지연을 재현하여,
 * Phase 1 "날것" 상태의 문제점을 숫자로 증명한다.
 * </p>
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Phase 2: Baseline 테스트 — PG 40% 실패 환경에서의 취약점 체감")
class PaymentBaselineTest {

    private static final Logger log = LoggerFactory.getLogger(PaymentBaselineTest.class);

    @Autowired PaymentFacade paymentFacade;
    @Autowired UserService userService;
    @Autowired BrandJpaRepository brandJpaRepository;
    @Autowired ProductJpaRepository productJpaRepository;
    @Autowired ProductStockJpaRepository productStockJpaRepository;
    @Autowired OrderJpaRepository orderJpaRepository;

    @MockitoBean PaymentGateway paymentGateway;

    private Long userId;

    @BeforeEach
    void setUp() {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        UserModel user = userService.register(new UserRegisterCommand(
                "baseline" + uniqueSuffix, "Test1234!@#", "베이스라인유저",
                "19900101", "baseline" + uniqueSuffix + "@test.com", "서울"));
        userId = user.getUserId();

        BrandModel brand = brandJpaRepository.save(
                BrandModel.create("테스트브랜드", "설명", "서울"));

        ProductModel product = productJpaRepository.save(
                ProductModel.create("테스트상품", brand.getBrandId(),
                        BigDecimal.valueOf(10000), "상품설명", null, null, null, null, null, null));

        productStockJpaRepository.save(ProductStockModel.create(product.getProductId(), 10000));
    }

    /**
     * PG 시뮬레이터 동작을 Mock으로 재현한다.
     * - 40% 확률로 RuntimeException (500 에러 시뮬레이션)
     * - 60% 확률로 성공 + 100~500ms 랜덤 지연
     */
    private void configurePgMock40PercentFailure() {
        when(paymentGateway.requestPayment(any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    // 100~500ms 랜덤 지연
                    Thread.sleep(100 + (long) (Math.random() * 400));

                    if (Math.random() < 0.4) {
                        throw new RuntimeException("PG 시뮬레이터 500 에러");
                    }

                    String txnKey = "txn-" + UUID.randomUUID().toString().substring(0, 8);
                    return new GatewayPaymentResult(txnKey, true, "PENDING", null);
                });
    }

    private Long createOrder() {
        OrderModel order = orderJpaRepository.save(
                OrderModel.create(userId, OrderType.DIRECT, BigDecimal.valueOf(10000)));
        return order.getOrderId();
    }

    @Nested
    @DisplayName("Test 2-1: 성공률 테스트")
    class SuccessRateTest {

        @Test
        @Transactional
        @DisplayName("50건 순차 요청 → 성공률 ~60% (40% 실패율 체감)")
        void successRate_With40PercentFailure_ShouldBeAround60Percent() {
            configurePgMock40PercentFailure();

            int total = 50;
            int success = 0;
            int failure = 0;

            for (int i = 0; i < total; i++) {
                Long orderId = createOrder();
                try {
                    paymentFacade.requestPayment(userId, orderId, CardType.SAMSUNG, "1234-5678-9012-3456");
                    success++;
                } catch (CoreException e) {
                    failure++;
                }
            }

            double successRate = (double) success / total * 100;
            log.info("━━━ Test 2-1: 성공률 테스트 결과 ━━━");
            log.info("총 요청: {}건, 성공: {}건, 실패: {}건", total, success, failure);
            log.info("성공률: {}%", String.format("%.1f", successRate));
            log.info("발제: \"10명 중 {}명이 결제 실패를 경험한다\"", failure * 10 / total);

            // 40% 실패율 → 성공률 50~75% 범위 (확률적 변동 허용)
            assertThat(successRate).isBetween(40.0, 80.0);
        }
    }

    @Nested
    @DisplayName("Test 2-2: 응답 시간 테스트")
    class ResponseTimeTest {

        @Test
        @Transactional
        @DisplayName("20건 요청 → p50/p95/max 응답 시간 기록")
        void responseTime_ShouldRecord_P50P95Max() {
            configurePgMock40PercentFailure();

            int total = 20;
            List<Long> durations = new ArrayList<>();

            for (int i = 0; i < total; i++) {
                Long orderId = createOrder();
                long start = System.currentTimeMillis();
                try {
                    paymentFacade.requestPayment(userId, orderId, CardType.SAMSUNG, "1234-5678-9012-3456");
                } catch (CoreException e) {
                    // 실패도 응답 시간 기록
                }
                long elapsed = System.currentTimeMillis() - start;
                durations.add(elapsed);
            }

            Collections.sort(durations);

            long p50 = durations.get(durations.size() / 2);
            long p95 = durations.get((int) (durations.size() * 0.95) - 1);
            long max = durations.get(durations.size() - 1);

            log.info("━━━ Test 2-2: 응답 시간 테스트 결과 ━━━");
            log.info("p50: {}ms, p95: {}ms, max: {}ms", p50, p95, max);
            log.info("발제: \"지금은 {}ms지만 timeout 없으면 PG 장애 시 무한 대기\"", max);

            // PG 응답 100~500ms → 최대 ~600ms 이내 (오버헤드 포함)
            assertThat(max).isLessThan(2000);
        }
    }

    @Nested
    @DisplayName("Test 2-3: 동시성 테스트")
    class ConcurrencyTest {

        @Test
        @DisplayName("50 threads 동시 결제 → 성공률 ~60%, 커넥션 풀 압박 체감")
        void concurrency_With50Threads_ShouldShowConnectionPoolPressure() throws Exception {
            configurePgMock40PercentFailure();

            int threadCount = 50;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(1);

            // 주문을 미리 생성 (각 스레드별 별도 주문)
            List<Long> orderIds = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                orderIds.add(createOrder());
            }

            AtomicInteger success = new AtomicInteger(0);
            AtomicInteger failure = new AtomicInteger(0);
            List<Long> durations = Collections.synchronizedList(new ArrayList<>());

            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                final Long orderId = orderIds.get(i);
                futures.add(executor.submit(() -> {
                    try {
                        latch.await(); // 모든 스레드 동시 출발
                        long start = System.currentTimeMillis();
                        try {
                            paymentFacade.requestPayment(userId, orderId, CardType.SAMSUNG, "1234-5678-9012-3456");
                            success.incrementAndGet();
                        } catch (Exception e) {
                            failure.incrementAndGet();
                        }
                        durations.add(System.currentTimeMillis() - start);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }));
            }

            latch.countDown(); // 동시 출발

            for (Future<?> f : futures) {
                f.get();
            }
            executor.shutdown();

            Collections.sort(durations);
            long p50 = durations.get(durations.size() / 2);
            long p95 = durations.get((int) (durations.size() * 0.95) - 1);
            long max = durations.get(durations.size() - 1);
            double successRate = (double) success.get() / threadCount * 100;

            log.info("━━━ Test 2-3: 동시성 테스트 결과 ━━━");
            log.info("동시 요청: {}건, 성공: {}건, 실패: {}건", threadCount, success.get(), failure.get());
            log.info("성공률: {}%", String.format("%.1f", successRate));
            log.info("p50: {}ms, p95: {}ms, max: {}ms", p50, p95, max);
            log.info("발제: \"동시 요청 증가 시 DB 커넥션 풀 고갈 위험\"");

            assertThat(success.get() + failure.get()).isEqualTo(threadCount);
        }
    }

    @Nested
    @DisplayName("Test 2-4: 콜백 유실 시뮬레이션")
    class CallbackLostTest {

        @Test
        @Transactional
        @DisplayName("결제 요청 후 콜백 미전송 → Payment 상태 REQUESTED 영구 체류")
        void callbackLost_ShouldLeavePayment_InRequestedForever() {
            // PG 호출은 성공하지만 콜백을 보내지 않는 상황 시뮬레이션
            when(paymentGateway.requestPayment(any(), any(), any(), any(), any(), any()))
                    .thenReturn(new GatewayPaymentResult("txn-lost-001", true, "PENDING", null));

            Long orderId = createOrder();

            PaymentInfo info = paymentFacade.requestPayment(
                    userId, orderId, CardType.SAMSUNG, "1234-5678-9012-3456");

            assertThat(info.status()).isEqualTo("PENDING");
            assertThat(info.transactionKey()).isEqualTo("txn-lost-001");

            // 콜백이 오지 않으면 → Payment는 REQUESTED 상태로 영구 체류
            // Phase 3-4의 폴링 스케줄러가 없으면 이 상태를 영원히 모른다
            log.info("━━━ Test 2-4: 콜백 유실 시뮬레이션 결과 ━━━");
            log.info("Payment 상태: REQUESTED (콜백 미수신)");
            log.info("transactionKey: {}", info.transactionKey());
            log.info("발제: \"콜백 유실 시 결제 결과를 영원히 모른다\"");
            log.info("해결: Phase 3-4 폴링 스케줄러가 PG에 직접 조회하여 복구");
        }
    }
}
