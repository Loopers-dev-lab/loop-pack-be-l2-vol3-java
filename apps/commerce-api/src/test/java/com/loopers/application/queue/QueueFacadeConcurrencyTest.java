package com.loopers.application.queue;

import com.loopers.domain.queue.QueueRepository;
import com.loopers.config.QueueProperties;
import com.loopers.interfaces.scheduler.QueueScheduler;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@Import(RedisTestContainersConfig.class)
@SpringBootTest
class QueueFacadeConcurrencyTest {

    @MockBean
    QueueScheduler queueScheduler;

    @Autowired
    private QueueFacade queueFacade;

    @Autowired
    private QueueRepository queueRepository;

    @Autowired
    private QueueProperties queueProperties;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @DisplayName("서로 다른 두 사용자가 동시에 진입하면, 유실 없이 모두 대기열에 들어가고 rank 는 0,1 이어야 한다.")
    @Test
    void enterConcurrently_assignsDistinctRanksWithoutLoss() throws InterruptedException {
        // arrange
        long firstUserId = 1L;
        long secondUserId = 2L;
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        // act
        executor.submit(() -> {
            awaitAndRun(startLatch, () -> queueFacade.enter(firstUserId));
            doneLatch.countDown();
        });
        executor.submit(() -> {
            awaitAndRun(startLatch, () -> queueFacade.enter(secondUserId));
            doneLatch.countDown();
        });

        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        // assert
        assertThat(queueRepository.isInWaiting(firstUserId)).isTrue();
        assertThat(queueRepository.isInWaiting(secondUserId)).isTrue();

        Optional<Long> firstRank = queueRepository.getRank(firstUserId);
        Optional<Long> secondRank = queueRepository.getRank(secondUserId);

        assertThat(firstRank).isPresent();
        assertThat(secondRank).isPresent();
        assertThat(firstRank.get()).isNotEqualTo(secondRank.get());
        assertThat(Set.of(firstRank.get(), secondRank.get())).isEqualTo(Set.of(0L, 1L));
    }

    @DisplayName("같은 사용자가 동시에 여러 번 진입해도 waiting 엔트리는 하나만 유지된다.")
    @Test
    void enterConcurrently_sameUser_keepsSingleWaitingEntry() throws InterruptedException {
        // arrange
        long userId = 1L;
        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        // act
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                awaitAndRun(startLatch, () -> queueFacade.enter(userId));
                doneLatch.countDown();
            });
        }

        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        List<Long> issuedUsers = queueRepository.issueTokens(
                threadCount,
                queueProperties.tokenTtlSeconds(),
                List.of(UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString())
        );

        // assert
        assertThat(issuedUsers).containsExactly(userId);
        assertThat(queueRepository.findToken(userId)).isPresent();
        assertThat(queueRepository.getRank(userId)).isEmpty();
    }

    private void awaitAndRun(CountDownLatch startLatch, Runnable action) {
        try {
            startLatch.await();
            action.run();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
