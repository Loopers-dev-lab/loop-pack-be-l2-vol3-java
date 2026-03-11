package com.loopers.concurrency;

import com.loopers.domain.point.PointAccount;
import com.loopers.domain.point.PointAccountRepository;
import com.loopers.domain.point.PointService;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 포인트 동시성 제어 테스트 (원자적 UPDATE)
 *
 * [동시 시작 패턴]
 * startLatch(CountDownLatch(1))를 사용하여 모든 스레드가 동시에 출발하도록 보장한다.
 */
@SpringBootTest
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class PointConcurrencyTest {

    @Autowired
    private PointService pointService;

    @Autowired
    private PointAccountRepository pointAccountRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("잔액 10000 포인트인 계좌에 10명이 동시에 1000 포인트씩 사용하면, 정확히 10명 모두 성공하고 잔액은 0이다")
    void 포인트_동시_사용_원자적_UPDATE() throws InterruptedException {
        // arrange
        PointAccount account = pointService.createAccount(1L);
        pointService.charge(1L, 10000);

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // act
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    pointService.use(1L, 1000);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        startLatch.countDown();

        latch.await();
        executor.shutdown();

        // assert
        assertThat(successCount.get()).isEqualTo(10);
        assertThat(failCount.get()).isEqualTo(0);

        PointAccount updated = pointAccountRepository.findByUserId(1L).orElseThrow();
        assertThat(updated.getBalance()).isZero();
    }
}
