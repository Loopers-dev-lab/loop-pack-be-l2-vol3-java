package com.loopers.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import com.loopers.application.coupon.CouponFacade;
import com.loopers.domain.coupon.CouponDiscountType;
import com.loopers.domain.coupon.CouponModel;
import com.loopers.infrastructure.coupon.CouponJpaRepository;
import com.loopers.infrastructure.coupon.OwnedCouponJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 쿠폰 발급 동시성 테스트 — 낙관적 락(@Version) 적용.
 *
 * CouponModel에 @Version 필드를 추가하여 낙관적 락으로 동시성을 제어한다.
 * - 충돌 시 ObjectOptimisticLockingFailureException 발생 → 트랜잭션 롤백
 * - 재시도 없이 실패 처리 (경합이 드문 일반 쿠폰 가정)
 * - 고경합 시 성공 수가 totalQuantity보다 적을 수 있지만, 초과 발급은 절대 발생하지 않는다
 *
 * 4가지 동시성 테스트 도구를 비교한다:
 * 1. new Thread() — 스레드 직접 생성 (동시 출발 보장 X)
 * 2. ExecutorService + CountDownLatch 기본 — 완료 대기만 보장
 * 3. ExecutorService + CountDownLatch 강화 — 동시 출발 + 완료 대기 보장
 * 4. CompletableFuture — 간결하지만 동시 출발 보장 X
 */
@DisplayName("쿠폰 발급 동시성 테스트")
@SpringBootTest
class CouponIssueConcurrencyTest {

    private static final int TOTAL_QUANTITY = 10;
    private static final int THREAD_COUNT = 10;

    @Autowired
    private CouponFacade couponFacade;

    @Autowired
    private CouponJpaRepository couponJpaRepository;

    @Autowired
    private OwnedCouponJpaRepository ownedCouponJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private Long couponId;

    @BeforeEach
    void setUp() {
        CouponModel coupon = couponJpaRepository.save(
                CouponModel.create("동시성 테스트 쿠폰", CouponDiscountType.FIXED, 5000,
                        null, TOTAL_QUANTITY, ZonedDateTime.now().plusMonths(3)));
        couponId = coupon.getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private ConcurrencyResult buildResult(AtomicInteger successCount, AtomicInteger failCount) {
        int actualOwned = (int) ownedCouponJpaRepository.countByCouponId(couponId);
        return new ConcurrencyResult(
                successCount.get(), failCount.get(), actualOwned);
    }

    private record ConcurrencyResult(
            int success, int fail, int actualOwned) {

        void printReport(String label) {
            System.out.println("\n========== " + label + " ==========");
            System.out.println("  총 수량(totalQuantity) : " + TOTAL_QUANTITY);
            System.out.println("  요청 수(threadCount)   : " + THREAD_COUNT);
            System.out.println("  성공 카운트            : " + success);
            System.out.println("  실패 카운트            : " + fail);
            System.out.println("  실제 OwnedCoupon       : " + actualOwned);
            System.out.println("  초과 발급 여부          : " + (actualOwned > TOTAL_QUANTITY ? "OVER-ISSUED" : "OK"));
            System.out.println("==========================================\n");
        }

        void assertCorrectness() {
            assertThat(actualOwned)
                    .as("실제 발급 수(%d)가 totalQuantity(%d)를 초과하면 안 된다",
                            actualOwned, TOTAL_QUANTITY)
                    .isLessThanOrEqualTo(TOTAL_QUANTITY);
        }
    }

    // =========================================================================
    // 방식 1: new Thread() — 스레드를 직접 생성
    //
    // 스레드 생성 시점이 제각각이라 실제 동시 실행 확률이 낮다.
    // 먼저 생성된 스레드가 작업을 완료한 뒤에야 다른 스레드가 시작될 수 있다.
    // → Race Condition 재현 확률이 가장 낮은 방식
    // =========================================================================

    @DisplayName("방식 1: new Thread()")
    @Nested
    class WithNewThread {

        @DisplayName("10명이 동시에 발급 요청 → totalQuantity(10)만큼만 발급되어야 한다")
        @RepeatedTest(3)
        void issue_withRawThreads() throws InterruptedException {
            // arrange
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);
            List<Thread> threads = new ArrayList<>();

            // act
            for (int i = 0; i < THREAD_COUNT; i++) {
                long userId = i + 1;
                Thread thread = new Thread(() -> {
                    try {
                        couponFacade.issueCoupon(couponId, userId);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        failCount.incrementAndGet();
                    }
                });
                threads.add(thread);
                thread.start();
            }

            for (Thread thread : threads) {
                thread.join();
            }

            // assert
            ConcurrencyResult result = buildResult(successCount, failCount);
            result.printReport("new Thread()");
            result.assertCorrectness();
        }
    }

    // =========================================================================
    // 방식 2: ExecutorService + CountDownLatch (기본 패턴)
    //
    // 스레드 풀로 미리 생성하고, doneLatch로 완료만 대기.
    // 동시 출발은 보장하지 않지만, 스레드 생성 비용이 실행과 분리되어
    // new Thread()보다는 동시성 확률이 높다.
    // =========================================================================

    @DisplayName("방식 2: ExecutorService + CountDownLatch (기본)")
    @Nested
    class WithBasicLatch {

        @DisplayName("10명이 동시에 발급 요청 → totalQuantity(10)만큼만 발급되어야 한다")
        @RepeatedTest(3)
        void issue_withBasicLatch() throws InterruptedException {
            // arrange
            ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
            CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            // act
            for (int i = 0; i < THREAD_COUNT; i++) {
                long userId = i + 1;
                executor.submit(() -> {
                    try {
                        couponFacade.issueCoupon(couponId, userId);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        failCount.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            doneLatch.await();
            executor.shutdown();

            // assert
            ConcurrencyResult result = buildResult(successCount, failCount);
            result.printReport("ExecutorService + CountDownLatch (기본)");
            result.assertCorrectness();
        }
    }

    // =========================================================================
    // 방식 3: ExecutorService + CountDownLatch (강화 패턴)
    //
    // readyLatch: 모든 스레드가 준비될 때까지 대기
    // startLatch: 한 번에 모든 스레드를 깨워 동시 출발
    // doneLatch: 모든 스레드 완료 대기
    //
    // 가장 높은 확률로 Race Condition을 재현한다.
    // =========================================================================

    @DisplayName("방식 3: ExecutorService + CountDownLatch (강화 — 동시 출발)")
    @Nested
    class WithEnhancedLatch {

        @DisplayName("10명이 동시에 발급 요청 → totalQuantity(10)만큼만 발급되어야 한다")
        @RepeatedTest(3)
        void issue_withEnhancedLatch() throws InterruptedException {
            // arrange
            ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
            CountDownLatch readyLatch = new CountDownLatch(THREAD_COUNT);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);
            List<String> errors = Collections.synchronizedList(new ArrayList<>());

            // act
            for (int i = 0; i < THREAD_COUNT; i++) {
                long userId = i + 1;
                executor.submit(() -> {
                    try {
                        readyLatch.countDown();
                        startLatch.await();
                        couponFacade.issueCoupon(couponId, userId);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        failCount.incrementAndGet();
                        errors.add(e.getMessage());
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            readyLatch.await();
            startLatch.countDown();
            doneLatch.await();
            executor.shutdown();

            // assert
            ConcurrencyResult result = buildResult(successCount, failCount);
            result.printReport("ExecutorService + CountDownLatch (강화)");
            result.assertCorrectness();
        }
    }

    // =========================================================================
    // 방식 4: CompletableFuture
    //
    // 코드가 간결하지만 동시 출발을 보장하지 않는다.
    // ForkJoinPool이 제출 순서대로 스케줄링하므로 순차 실행될 가능성이 있다.
    // =========================================================================

    @DisplayName("방식 4: CompletableFuture")
    @Nested
    class WithCompletableFuture {

        @DisplayName("10명이 동시에 발급 요청 → totalQuantity(10)만큼만 발급되어야 한다")
        @RepeatedTest(3)
        void issue_withCompletableFuture() {
            // arrange
            ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            // act
            List<CompletableFuture<Void>> futures = IntStream.range(0, THREAD_COUNT)
                    .mapToObj(i -> CompletableFuture.runAsync(() -> {
                        try {
                            couponFacade.issueCoupon(couponId, (long) (i + 1));
                            successCount.incrementAndGet();
                        } catch (Exception e) {
                            failCount.incrementAndGet();
                        }
                    }, executor))
                    .toList();

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            executor.shutdown();

            // assert
            ConcurrencyResult result = buildResult(successCount, failCount);
            result.printReport("CompletableFuture");
            result.assertCorrectness();
        }
    }
}
