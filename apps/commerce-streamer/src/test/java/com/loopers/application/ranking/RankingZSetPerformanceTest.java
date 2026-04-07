package com.loopers.application.ranking;

import com.loopers.domain.ranking.RankingKeyGenerator;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(RedisTestContainersConfig.class)
@DisplayName("ZSET 대량 데이터 성능 측정 — L1 캐시 제거 근거 검증")
class RankingZSetPerformanceTest {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @ParameterizedTest(name = "ZSET {0}개 멤버 → ZREVRANGE Top-20 성능")
    @ValueSource(ints = {1_000, 10_000, 50_000, 100_000})
    @DisplayName("대량 ZSET에서 ZREVRANGE Top-20 응답 시간 측정")
    void zrevrangePerformance(int memberCount) {
        LocalDate date = LocalDate.of(2026, 4, 6);
        String key = RankingKeyGenerator.dailyKey(date);

        long seedStart = System.nanoTime();
        for (int i = 1; i <= memberCount; i++) {
            redisTemplate.opsForZSet().add(key, String.valueOf(i), Math.random() * 1_000_000);
        }
        long seedMs = (System.nanoTime() - seedStart) / 1_000_000;

        int iterations = 100;
        long totalNanos = 0;
        long minNanos = Long.MAX_VALUE;
        long maxNanos = Long.MIN_VALUE;

        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            Set<ZSetOperations.TypedTuple<String>> result =
                    redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, 19);
            long elapsed = System.nanoTime() - start;
            totalNanos += elapsed;
            minNanos = Math.min(minNanos, elapsed);
            maxNanos = Math.max(maxNanos, elapsed);

            assertThat(result).hasSize(20);
        }

        double avgMs = (totalNanos / (double) iterations) / 1_000_000.0;
        double minMs = minNanos / 1_000_000.0;
        double maxMs = maxNanos / 1_000_000.0;

        System.out.println("=== ZREVRANGE Top-20 성능 ===");
        System.out.println("  ZSET size: " + memberCount);
        System.out.println("  Seed time: " + seedMs + "ms");
        System.out.println("  Iterations: " + iterations);
        System.out.printf("  Avg: %.3fms%n", avgMs);
        System.out.printf("  Min: %.3fms%n", minMs);
        System.out.printf("  Max: %.3fms%n", maxMs);
        System.out.println("  → " + (avgMs < 1.0 ? "캐싱 불필요 (< 1ms)" : "캐싱 검토 필요 (> 1ms)"));
    }

    @Test
    @DisplayName("ZREVRANGEBYSCORE (cursor 방식) — 10만 멤버 성능 측정")
    void zrevrangebyscorePerformance() {
        LocalDate date = LocalDate.of(2026, 4, 6);
        String key = RankingKeyGenerator.dailyKey(date);
        int memberCount = 100_000;

        for (int i = 1; i <= memberCount; i++) {
            redisTemplate.opsForZSet().add(key, String.valueOf(i), Math.random() * 1_000_000);
        }

        int iterations = 100;
        long totalNanos = 0;

        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            Set<ZSetOperations.TypedTuple<String>> result =
                    redisTemplate.opsForZSet().reverseRangeByScoreWithScores(
                            key, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 0, 20);
            long elapsed = System.nanoTime() - start;
            totalNanos += elapsed;

            assertThat(result).hasSize(20);
        }

        double avgMs = (totalNanos / (double) iterations) / 1_000_000.0;
        System.out.println("=== ZREVRANGEBYSCORE Top-20 (cursor) ===");
        System.out.println("  ZSET size: " + memberCount);
        System.out.printf("  Avg: %.3fms (over %d iterations)%n", avgMs, iterations);
    }

    @Test
    @DisplayName("ZINCRBY 대량 쓰기 성능 — 10만 멤버에 대한 연속 점수 누적")
    void zincrbyBulkWritePerformance() {
        LocalDate date = LocalDate.of(2026, 4, 6);
        String key = RankingKeyGenerator.dailyKey(date);

        for (int i = 1; i <= 100_000; i++) {
            redisTemplate.opsForZSet().add(key, String.valueOf(i), 0);
        }

        int writeCount = 10_000;
        long start = System.nanoTime();
        for (int i = 0; i < writeCount; i++) {
            long productId = (long) (Math.random() * 100_000) + 1;
            redisTemplate.opsForZSet().incrementScore(key, String.valueOf(productId), 0.1);
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        System.out.println("=== ZINCRBY 대량 쓰기 ===");
        System.out.println("  ZSET size: 100,000");
        System.out.println("  Write count: " + writeCount);
        System.out.println("  Total: " + elapsedMs + "ms");
        System.out.printf("  Avg per write: %.3fms%n", elapsedMs / (double) writeCount);
    }

    @Test
    @DisplayName("동시 읽기/쓰기 — ZREVRANGE 중 ZINCRBY 수행 시 성능 영향")
    void concurrentReadWritePerformance() throws InterruptedException {
        LocalDate date = LocalDate.of(2026, 4, 6);
        String key = RankingKeyGenerator.dailyKey(date);

        for (int i = 1; i <= 50_000; i++) {
            redisTemplate.opsForZSet().add(key, String.valueOf(i), Math.random() * 1_000_000);
        }

        long[] readTotalNanos = {0};
        int readIterations = 200;
        long[] writeTotalNanos = {0};
        int writeIterations = 1000;

        Thread writer = new Thread(() -> {
            long start = System.nanoTime();
            for (int i = 0; i < writeIterations; i++) {
                long productId = (long) (Math.random() * 50_000) + 1;
                redisTemplate.opsForZSet().incrementScore(key, String.valueOf(productId), 0.1);
            }
            writeTotalNanos[0] = System.nanoTime() - start;
        });

        Thread reader = new Thread(() -> {
            long start = System.nanoTime();
            for (int i = 0; i < readIterations; i++) {
                redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, 19);
            }
            readTotalNanos[0] = System.nanoTime() - start;
        });

        writer.start();
        reader.start();
        writer.join();
        reader.join();

        double readAvgMs = (readTotalNanos[0] / (double) readIterations) / 1_000_000.0;
        double writeAvgMs = (writeTotalNanos[0] / (double) writeIterations) / 1_000_000.0;

        System.out.println("=== 동시 읽기/쓰기 성능 (50K ZSET) ===");
        System.out.printf("  Read avg (ZREVRANGE Top-20): %.3fms (over %d iterations)%n", readAvgMs, readIterations);
        System.out.printf("  Write avg (ZINCRBY): %.3fms (over %d iterations)%n", writeAvgMs, writeIterations);
    }
}
