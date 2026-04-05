package com.loopers.application.queue;

import com.loopers.domain.user.UserModel;
import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
    "queue.token-ttl-seconds=1",
    "queue.scheduler.fixed-delay-ms=1000",
    "queue.scheduler.max-batch-size=5",
    "queue.throughput.db-connection-utilization=0.8",
    "queue.throughput.avg-order-processing-ms=250",
    "datasource.mysql-jpa.main.maximum-pool-size=10"
})
@Import(RedisTestContainersConfig.class)
class QueueServiceIntegrationTest {

    @Autowired
    private QueueService queueService;

    @Autowired
    private QueueThroughputPolicy queueThroughputPolicy;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    @DisplayName("동시 진입 시 순번이 중복 없이 1..N으로 부여된다.")
    @Test
    void assignsUniqueSequentialPositions_underConcurrentEnter() throws InterruptedException {
        int userCount = 30;
        List<UserModel> users = createUsers(userCount);

        Set<Long> positions = ConcurrentHashMap.newKeySet();
        runConcurrently(userCount, users, user -> {
            QueuePositionInfo entered = queueService.enter(user.getLoginId(), "Test1234!");
            positions.add(entered.position());
        });

        List<Long> sorted = positions.stream().sorted(Comparator.naturalOrder()).toList();
        assertThat(sorted).containsExactlyElementsOf(LongStream.rangeClosed(1, userCount).boxed().toList());
        assertThat(queueService.getTotalWaitingCount()).isEqualTo(userCount);
    }

    @DisplayName("토큰 TTL이 지나면 검증에 실패한다.")
    @Test
    void expiresToken_afterTtl() throws InterruptedException {
        createUser("token-user");
        queueService.enter("token-user", "Test1234!");
        Map<Long, String> admitted = queueService.admitNextBatch(1);
        String token = admitted.values().iterator().next();

        Thread.sleep(1200L);

        assertThatThrownBy(() -> queueService.validateTokenOrThrow("token-user", "Test1234!", token))
            .hasMessageContaining("유효한 입장 토큰");
    }

    @DisplayName("처리량 이상 유입 시에도 배치 크기 만큼만 입장시키고 나머지는 대기한다.")
    @Test
    void admitsOnlyBatchSize_whenRequestsExceedCapacity() {
        int batchSize = queueThroughputPolicy.calculateBatchSize();
        int totalUsers = batchSize + 7;
        List<UserModel> users = createUsers(totalUsers);
        for (UserModel user : users) {
            queueService.enter(user.getLoginId(), "Test1234!");
        }

        Map<Long, String> issued = queueService.admitNextBatch(batchSize);

        assertThat(issued).hasSize(batchSize);
        assertThat(queueService.getTotalWaitingCount()).isEqualTo(totalUsers - batchSize);
    }

    private List<UserModel> createUsers(int size) {
        List<UserModel> users = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            users.add(createUser("user" + i));
        }
        return users;
    }

    private UserModel createUser(String loginId) {
        String encodedPassword = passwordEncoder.encode("Test1234!");
        return userJpaRepository.save(
            UserModel.createWithEncodedPassword(
                loginId,
                encodedPassword,
                "사용자",
                LocalDate.of(1991, 1, 1),
                loginId + "@example.com"
            )
        );
    }

    private void runConcurrently(int threadCount, List<UserModel> users, UserTask task) throws InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);

        for (UserModel user : users) {
            executorService.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    task.run(user);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        start.countDown();
        done.await();
        executorService.shutdown();
        assertThat(executorService.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }

    @FunctionalInterface
    private interface UserTask {
        void run(UserModel user);
    }
}
