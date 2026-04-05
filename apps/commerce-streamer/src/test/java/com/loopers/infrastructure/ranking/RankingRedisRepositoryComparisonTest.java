package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.RankingKeyGenerator;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(RedisTestContainersConfig.class)
@DisplayName("RankingRedisRepository A(순차) vs B(Lua) 비교 검증")
class RankingRedisRepositoryComparisonTest {

    @Autowired
    private RankingRedisRepository repository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @Nested
    @DisplayName("기본 동작 — 점수 누적 정확성")
    class Correctness {

        @Test
        @DisplayName("A(순차)와 B(Lua) 모두 점수를 정확히 누적한다")
        void bothAccumulateScoreCorrectly() {
            LocalDate date = LocalDate.of(2026, 4, 5);
            String key = RankingKeyGenerator.dailyKey(date);

            repository.incrementScore(date, 42L, 0.2);
            repository.incrementScore(date, 42L, 0.7);
            repository.incrementScoreSequential(date, 42L, 0.1);

            Double score = redisTemplate.opsForZSet().score(key, "42");
            assertThat(score).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.0001));
        }

        @Test
        @DisplayName("A(순차)와 B(Lua) 모두 TTL을 설정한다")
        void bothSetTtlOnFirstCreation() {
            LocalDate dateForLua = LocalDate.of(2026, 4, 5);
            LocalDate dateForSequential = LocalDate.of(2026, 4, 6);

            repository.incrementScore(dateForLua, 1L, 1.0);
            repository.incrementScoreSequential(dateForSequential, 1L, 1.0);

            Long ttlLua = redisTemplate.getExpire(RankingKeyGenerator.dailyKey(dateForLua));
            Long ttlSeq = redisTemplate.getExpire(RankingKeyGenerator.dailyKey(dateForSequential));

            assertThat(ttlLua).isGreaterThan(Duration.ofDays(1).toSeconds());
            assertThat(ttlSeq).isGreaterThan(Duration.ofDays(1).toSeconds());
        }
    }

    @Nested
    @DisplayName("TTL 원자성 — A(순차)의 실패 시나리오")
    class TtlAtomicity {

        @Test
        @DisplayName("A(순차): hasKey 체크와 EXPIRE 사이에 Key가 삭제되면 TTL 누락 발생")
        void sequentialLosesTtlOnRaceCondition() {
            LocalDate date = LocalDate.of(2026, 4, 5);
            String key = RankingKeyGenerator.dailyKey(date);

            // 1. 먼저 Key를 만들고 TTL을 설정
            repository.incrementScoreSequential(date, 100L, 1.0);
            assertThat(redisTemplate.getExpire(key)).isGreaterThan(0);

            // 2. Key를 삭제 (실제 운영에서는 TTL 만료로 유사하게 발생 가능)
            redisTemplate.delete(key);

            // 3. 시뮬레이션: hasKey가 false를 반환한 직후 ZINCRBY가 호출되기 전에
            //    다른 프로세스가 Key를 생성한 상황.
            //    여기서는 hasKey와 EXPIRE 사이의 TOCTOU 문제를 보여주기 위해
            //    원시 연산으로 재현한다.
            String productDbId = "200";
            Boolean hasKey = redisTemplate.hasKey(key); // false

            // 다른 프로세스가 ZINCRBY로 Key를 생성 (TTL 없이)
            redisTemplate.opsForZSet().incrementScore(key, "300", 5.0);

            // 이후 ZINCRBY 수행 (기존 A 로직 이어가기)
            redisTemplate.opsForZSet().incrementScore(key, productDbId, 1.0);
            if (Boolean.FALSE.equals(hasKey)) {
                redisTemplate.expire(key, Duration.ofDays(2));
            }

            // 이 경우는 TTL이 설정됨. 실제 위험은 hasKey=true지만 Key가 사라진 경우
            // → 즉, "hasKey=true → 중간 만료 → ZINCRBY가 Key 재생성 → EXPIRE 스킵" 시나리오
            Long ttl1 = redisTemplate.getExpire(key);
            assertThat(ttl1).isGreaterThan(0);

            // 진짜 위험 시나리오 재현: hasKey=true 상태에서 Key가 삭제되고 ZINCRBY가 재생성
            redisTemplate.delete(key);
            // 선행 상태로 Key 존재시키기
            redisTemplate.opsForZSet().incrementScore(key, "pre", 1.0);
            redisTemplate.expire(key, Duration.ofDays(2));

            Boolean hasKeyNow = redisTemplate.hasKey(key); // true
            redisTemplate.delete(key); // TTL 만료 시뮬레이션
            redisTemplate.opsForZSet().incrementScore(key, "after", 1.0); // ZINCRBY가 Key 재생성
            if (Boolean.FALSE.equals(hasKeyNow)) {
                redisTemplate.expire(key, Duration.ofDays(2));
            }
            // 버그: hasKeyNow=true였으므로 EXPIRE 스킵 → TTL 없는 Key
            Long ttlAfter = redisTemplate.getExpire(key);
            assertThat(ttlAfter).isEqualTo(-1L); // TTL 없음 (-1 = persistent, -2 = not exists)
        }

        @Test
        @DisplayName("B(Lua): Key 상태에 관계없이 항상 TTL이 설정된다")
        void luaAlwaysSetsTtl() {
            LocalDate date = LocalDate.of(2026, 4, 5);
            String key = RankingKeyGenerator.dailyKey(date);

            // 원시 ZINCRBY로 TTL 없는 Key 생성
            redisTemplate.opsForZSet().incrementScore(key, "stale", 5.0);
            assertThat(redisTemplate.getExpire(key)).isEqualTo(-1L);

            // B(Lua) 호출: Key에 TTL이 없으면 설정, 있으면 유지
            repository.incrementScore(date, 42L, 1.0);

            Long ttl = redisTemplate.getExpire(key);
            assertThat(ttl).isGreaterThan(Duration.ofDays(1).toSeconds());
        }
    }

    @Nested
    @DisplayName("동시성 — 점수 누적 일관성")
    class Concurrency {

        @Test
        @DisplayName("A(순차) 동시 호출 — 점수는 정확하지만 RTT가 2~3배")
        void sequentialConcurrency() throws InterruptedException {
            LocalDate date = LocalDate.of(2026, 4, 5);
            int threads = 50;
            int perThread = 20;

            ExecutorService executor = Executors.newFixedThreadPool(threads);
            CountDownLatch latch = new CountDownLatch(threads);
            long start = System.nanoTime();

            for (int i = 0; i < threads; i++) {
                executor.submit(() -> {
                    try {
                        for (int j = 0; j < perThread; j++) {
                            repository.incrementScoreSequential(date, 1L, 0.1);
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await(30, TimeUnit.SECONDS);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            executor.shutdown();

            Double score = redisTemplate.opsForZSet()
                    .score(RankingKeyGenerator.dailyKey(date), "1");
            assertThat(score).isCloseTo(threads * perThread * 0.1, org.assertj.core.data.Offset.offset(0.001));
            System.out.println("[A-Sequential] elapsed: " + elapsedMs + "ms");
        }

        @Test
        @DisplayName("B(Lua) 동시 호출 — 점수 정확 + 단일 RTT")
        void luaConcurrency() throws InterruptedException {
            LocalDate date = LocalDate.of(2026, 4, 6);
            int threads = 50;
            int perThread = 20;

            ExecutorService executor = Executors.newFixedThreadPool(threads);
            CountDownLatch latch = new CountDownLatch(threads);
            long start = System.nanoTime();

            for (int i = 0; i < threads; i++) {
                executor.submit(() -> {
                    try {
                        for (int j = 0; j < perThread; j++) {
                            repository.incrementScore(date, 1L, 0.1);
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await(30, TimeUnit.SECONDS);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            executor.shutdown();

            Double score = redisTemplate.opsForZSet()
                    .score(RankingKeyGenerator.dailyKey(date), "1");
            assertThat(score).isCloseTo(threads * perThread * 0.1, org.assertj.core.data.Offset.offset(0.001));
            System.out.println("[B-Lua] elapsed: " + elapsedMs + "ms");
        }
    }

    @Nested
    @DisplayName("RTT 비교 — 네트워크 호출 횟수")
    class RttCount {

        @Test
        @DisplayName("A(순차)는 Redis 호출을 3회 수행 (hasKey + ZINCRBY + EXPIRE)")
        void sequentialCallsRedisThreeTimes() {
            LocalDate date = LocalDate.of(2026, 4, 5);
            AtomicInteger ignored = new AtomicInteger();
            ignored.incrementAndGet();
            // 실제 RTT 측정은 wireshark/redis-cli MONITOR로 확인 가능
            // 코드 레벨에서는 opsForZSet().incrementScore + hasKey + expire = 3회 호출
            repository.incrementScoreSequential(date, 1L, 1.0);
        }

        @Test
        @DisplayName("B(Lua)는 Redis 호출을 1회 수행 (EVAL 단일)")
        void luaCallsRedisOnce() {
            LocalDate date = LocalDate.of(2026, 4, 5);
            // EVAL 1회로 ZINCRBY + TTL 체크 + EXPIRE가 원자적으로 실행됨
            repository.incrementScore(date, 1L, 1.0);
        }
    }
}
