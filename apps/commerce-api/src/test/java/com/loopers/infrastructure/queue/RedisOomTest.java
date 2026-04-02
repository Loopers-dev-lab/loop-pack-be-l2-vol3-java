package com.loopers.infrastructure.queue;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("P8: Redis 메모리 고갈 (maxmemory OOM) 테스트")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RedisOomTest {

    @SuppressWarnings("resource")
    private static GenericContainer<?> redisContainer;
    private static LettuceConnectionFactory connectionFactory;
    private static RedisTemplate<String, String> redisTemplate;
    private static WaitingQueueRedisRepository waitingQueueRepository;
    private static EntryTokenRedisRepository entryTokenRepository;

    @BeforeAll
    static void startRedis() {
        redisContainer = new GenericContainer<>(DockerImageName.parse("redis:latest"))
                .withExposedPorts(6379)
                .withCommand("redis-server", "--maxmemory", "2mb", "--maxmemory-policy", "noeviction");
        redisContainer.start();

        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(
                redisContainer.getHost(), redisContainer.getMappedPort(6379));
        connectionFactory = new LettuceConnectionFactory(config);
        connectionFactory.afterPropertiesSet();

        redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(connectionFactory);
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(new StringRedisSerializer());
        redisTemplate.afterPropertiesSet();

        waitingQueueRepository = new WaitingQueueRedisRepository(redisTemplate);
        entryTokenRepository = new EntryTokenRedisRepository(redisTemplate);
    }

    @AfterAll
    static void stopRedis() {
        connectionFactory.destroy();
        redisContainer.stop();
    }

    @BeforeEach
    void cleanUp() {
        try {
            redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
        } catch (Exception ignored) {
        }
    }

    @Test
    @Order(1)
    @DisplayName("정상 상태: 진입 + 조회 + 토큰 발급 모두 정상")
    void normalState_allOperationsWork() {
        boolean entered = waitingQueueRepository.enter(1L, 1000.0);
        assertThat(entered).isTrue();
        assertThat(waitingQueueRepository.getPosition(1L)).hasValue(1L);
        assertThat(waitingQueueRepository.getTotalCount()).isEqualTo(1);

        entryTokenRepository.issue(1L, "token-1", 300);
        assertThat(entryTokenRepository.findToken(1L)).hasValue("token-1");
    }

    @Test
    @Order(2)
    @DisplayName("OOM 유발 후 enter() 시 RedisSystemException 발생")
    void oom_enterThrows() {
        int oomPoint = fillUntilOom();
        assertThat(oomPoint).as("OOM이 발생해야 함").isGreaterThan(0);

        boolean exceptionThrown = false;
        try {
            waitingQueueRepository.enter(999999L, 999999000.0);
        } catch (RedisSystemException e) {
            exceptionThrown = true;
            assertThat(e.getCause().getMessage()).contains("OOM");
        }

        assertThat(exceptionThrown).as("enter()가 OOM 예외를 던져야 함").isTrue();
    }

    @Test
    @Order(3)
    @DisplayName("OOM 중: 읽기 연산(ZRANK, ZCARD)은 정상 동작 — noeviction 정책")
    void duringOom_readOperationsWork() {
        waitingQueueRepository.enter(1L, 1000.0);
        waitingQueueRepository.enter(2L, 2000.0);
        waitingQueueRepository.enter(3L, 3000.0);

        fillUntilOom();

        Optional<Long> position = waitingQueueRepository.getPosition(1L);
        assertThat(position).isPresent();

        long count = waitingQueueRepository.getTotalCount();
        assertThat(count).isGreaterThanOrEqualTo(3);
    }

    @Test
    @Order(4)
    @DisplayName("OOM 중: ZPOPMIN(메모리 해제)은 동작 — 기존 데이터 pop 가능")
    void duringOom_popNWorks() {
        for (int i = 1; i <= 5; i++) {
            waitingQueueRepository.enter((long) i, i * 1000.0);
        }

        fillUntilOom();

        List<Long> popped = waitingQueueRepository.popN(2);
        assertThat(popped).isNotEmpty();
    }

    @Test
    @Order(5)
    @DisplayName("OOM 중: 토큰 SET(새 키 생성)은 실패")
    void duringOom_tokenIssueFails() {
        fillUntilOom();

        boolean exceptionThrown = false;
        try {
            entryTokenRepository.issue(88888L, "test-token", 300);
        } catch (RedisSystemException e) {
            exceptionThrown = true;
            assertThat(e.getCause().getMessage()).contains("OOM");
        }

        assertThat(exceptionThrown).as("토큰 SET이 OOM 예외를 던져야 함").isTrue();
    }

    @Test
    @Order(6)
    @DisplayName("OOM 해소 후: FLUSHALL → 진입 정상화")
    void afterRecovery_enterWorksAgain() {
        fillUntilOom();

        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();

        boolean result = waitingQueueRepository.enter(1L, 1000.0);
        assertThat(result).isTrue();
        assertThat(waitingQueueRepository.getPosition(1L)).hasValue(1L);
    }

    private int fillUntilOom() {
        int count = 0;
        for (int i = 0; i < 100_000; i++) {
            try {
                redisTemplate.opsForValue().set("fill:" + i, "x".repeat(100));
                count++;
            } catch (Exception e) {
                return count;
            }
        }
        return count;
    }
}
