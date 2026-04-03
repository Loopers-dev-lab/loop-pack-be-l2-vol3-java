package com.loopers.application.queue;

import com.loopers.application.order.OrderCreateCommand;
import com.loopers.application.order.OrderFacade;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.product.Money;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.Stock;
import com.loopers.domain.queue.EntryTokenRepository;
import com.loopers.domain.queue.WaitingQueueRepository;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class QueueConcurrencyTest {

    private static final String QUEUE_KEY = "waiting-queue:order";

    // 스케줄러가 테스트 중 자동 실행되어 토큰을 발급하면 동시성 검증이 불안정해지므로 비활성화
    @MockBean
    private EntryTokenScheduler entryTokenScheduler;

    @Autowired
    private WaitingQueueRepository waitingQueueRepository;

    @Autowired
    private EntryTokenRepository entryTokenRepository;

    @Autowired
    private OrderFacade orderFacade;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplateMaster;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        redisTemplateMaster.delete(QUEUE_KEY);
        for (long i = 1; i <= 100; i++) {
            entryTokenRepository.delete(i);
        }
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("대기열 동시 진입")
    class ConcurrentEnqueue {

        @Test
        @DisplayName("100명이 동시에 대기열에 진입하면 중복 없이 100명이 등록된다")
        void 서로_다른_유저_100명_동시_진입() throws InterruptedException {
            // arrange
            int threadCount = 100;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);

            // act
            for (int i = 1; i <= threadCount; i++) {
                long userId = i;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        waitingQueueRepository.enqueue(userId, System.currentTimeMillis());
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

            // assert — Redis ZADD는 원자적이므로 100명 모두 정확히 등록됨
            assertThat(waitingQueueRepository.getTotalCount()).isEqualTo(100);
        }

        @Test
        @DisplayName("동일 유저가 10회 동시에 진입해도 대기열에 1명만 존재한다")
        void 동일_유저_10회_동시_진입() throws InterruptedException {
            // arrange
            long userId = 1L;
            int threadCount = 10;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);

            // act
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        waitingQueueRepository.enqueue(userId, System.currentTimeMillis());
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

            // assert — Sorted Set 멤버는 unique: 같은 userId는 score만 갱신되고 1명만 존재
            assertThat(waitingQueueRepository.getTotalCount()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("스케줄러와 진입 동시 실행")
    class ConcurrentSchedulerAndEnqueue {

        @Test
        @DisplayName("스케줄러 실행 중 신규 진입이 발생해도 진입한 유저가 큐에서 소실되지 않는다")
        void 스케줄러와_진입_동시_실행() throws InterruptedException {
            // arrange — 10명 먼저 진입 (스케줄러 대상)
            for (long i = 1; i <= 10; i++) {
                waitingQueueRepository.enqueue(i, System.currentTimeMillis() + i);
            }

            // 스케줄러 실행 + 10명 추가 진입을 동시에 수행
            ExecutorService executor = Executors.newFixedThreadPool(11);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(11);

            // act
            executor.submit(() -> {
                try {
                    startLatch.await();
                    // 실제 스케줄러 로직: ZRANGE + SET NX + 즉시 ZREM (새 설계)
                    List<Long> candidates = waitingQueueRepository.peekBatch(14);
                    for (Long uid : candidates) {
                        entryTokenRepository.issueIfAbsent(uid, "token-" + uid, 300L);
                        waitingQueueRepository.remove(uid); // 토큰 발급 후 즉시 ZREM
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
            for (long i = 11; i <= 20; i++) {
                long userId = i;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        waitingQueueRepository.enqueue(userId, System.currentTimeMillis() + userId);
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

            // assert — 신규 진입(11~20)한 유저가 소실되지 않았음을 검증
            // 스케줄러 ZRANGE 시점에 따라 일부가 함께 처리될 수 있으므로
            // "큐에 있거나 토큰이 있어야 한다" (둘 다 없으면 유저 소실)
            for (long i = 11; i <= 20; i++) {
                boolean inQueue  = waitingQueueRepository.getPosition(i) != null;
                boolean hasToken = entryTokenRepository.existsByUserId(i);
                assertThat(inQueue || hasToken)
                        .as("userId=%d 가 큐와 토큰 어디에도 없음 — 소실 발생", i)
                        .isTrue();
            }
        }
    }

    @Nested
    @DisplayName("토큰 동시 사용")
    class ConcurrentTokenUsage {

        @Test
        @DisplayName("동일 유저가 토큰 1개로 동시에 주문 2번 시도하면 재고 제약으로 1명만 성공한다")
        void 토큰_동시_사용_시도() throws InterruptedException {
            // arrange — 재고 1개 상품, userId=1에게 토큰 발급
            long userId = 1L;
            Brand brand = brandJpaRepository.save(new Brand("나이키"));
            Product product = productJpaRepository.save(
                    new Product(brand.getId(), "나이키 에어맥스", new Money(10000), new Stock(1)));
            entryTokenRepository.issueIfAbsent(userId, "test-token", 300L);
            OrderCreateCommand command = new OrderCreateCommand(
                    List.of(new OrderCreateCommand.Item(product.getId(), 1)), null);

            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(2);

            // act — 동일 userId로 동시에 주문 2번 시도
            for (int i = 0; i < 2; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        orderFacade.create(userId, command);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        // 재고 부족(BAD_REQUEST) 또는 비관적 락 타임아웃
                        failCount.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }
            startLatch.countDown();
            doneLatch.await();
            executor.shutdown();

            // assert — 재고 1개 → 1명만 성공, 1명 실패
            assertThat(successCount.get()).isEqualTo(1);
            assertThat(failCount.get()).isEqualTo(1);
        }
    }
}
