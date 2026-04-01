package com.loopers.infrastructure.queue;

import com.loopers.domain.queue.EntryTokenRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EntryTokenRepositoryImpl Redis 통합 테스트")
@SpringBootTest
@ActiveProfiles("test")
class EntryTokenRepositoryImplTest {

    @Autowired
    private EntryTokenRepository entryTokenRepository;

    @Autowired
    @Qualifier("redisTemplateMaster")
    private RedisTemplate<String, String> redisTemplate;

    private static final String KEY_PREFIX = "order:entry-token:";

    @AfterEach
    void tearDown() {
        redisTemplate.delete(KEY_PREFIX + "1");
        redisTemplate.delete(KEY_PREFIX + "42");
        redisTemplate.delete(KEY_PREFIX + "99");
    }

    // ============================
    // setIfAbsent
    // ============================
    @Nested
    @DisplayName("setIfAbsent()")
    class SetIfAbsent {

        @Test
        @DisplayName("신규 토큰 저장 시 true를 반환하고 TTL이 설정된다")
        void setIfAbsent_New_ShouldReturnTrueWithTTL() {
            boolean result = entryTokenRepository.setIfAbsent(1L, "token-uuid", Duration.ofSeconds(10));

            assertThat(result).isTrue();
            assertThat(entryTokenRepository.get(1L)).isEqualTo("token-uuid");

            Long ttl = redisTemplate.getExpire(KEY_PREFIX + "1", TimeUnit.SECONDS);
            assertThat(ttl).isGreaterThan(0).isLessThanOrEqualTo(10);
        }

        @Test
        @DisplayName("이미 존재하면 false를 반환하고 기존 값을 유지한다")
        void setIfAbsent_Duplicate_ShouldReturnFalseAndKeepOriginal() {
            entryTokenRepository.setIfAbsent(1L, "original-token", Duration.ofSeconds(60));

            boolean result = entryTokenRepository.setIfAbsent(1L, "new-token", Duration.ofSeconds(60));

            assertThat(result).isFalse();
            assertThat(entryTokenRepository.get(1L)).isEqualTo("original-token");
        }
    }

    // ============================
    // get
    // ============================
    @Nested
    @DisplayName("get()")
    class Get {

        @Test
        @DisplayName("존재하는 토큰을 반환한다")
        void get_Exists_ShouldReturnToken() {
            entryTokenRepository.setIfAbsent(1L, "my-token", Duration.ofSeconds(60));
            assertThat(entryTokenRepository.get(1L)).isEqualTo("my-token");
        }

        @Test
        @DisplayName("없는 토큰은 null을 반환한다")
        void get_NotExists_ShouldReturnNull() {
            assertThat(entryTokenRepository.get(99L)).isNull();
        }

        @Test
        @DisplayName("TTL 만료 후 null을 반환한다")
        void get_AfterTTL_ShouldReturnNull() throws InterruptedException {
            entryTokenRepository.setIfAbsent(1L, "short-lived", Duration.ofSeconds(1));
            Thread.sleep(2000);
            assertThat(entryTokenRepository.get(1L)).isNull();
        }
    }

    // ============================
    // delete
    // ============================
    @Nested
    @DisplayName("delete()")
    class Delete {

        @Test
        @DisplayName("존재하는 토큰 삭제 시 true를 반환한다")
        void delete_Exists_ShouldReturnTrue() {
            entryTokenRepository.setIfAbsent(1L, "token", Duration.ofSeconds(60));
            assertThat(entryTokenRepository.delete(1L)).isTrue();
            assertThat(entryTokenRepository.get(1L)).isNull();
        }

        @Test
        @DisplayName("없는 토큰 삭제 시 false를 반환한다")
        void delete_NotExists_ShouldReturnFalse() {
            assertThat(entryTokenRepository.delete(99L)).isFalse();
        }
    }

    // ============================
    // validateAndDelete (Lua script)
    // ============================
    @Nested
    @DisplayName("validateAndDelete() — Lua 원자적 검증+삭제")
    class ValidateAndDelete {

        @Test
        @DisplayName("올바른 토큰이면 true를 반환하고 삭제한다")
        void validateAndDelete_ValidToken_ShouldReturnTrueAndDelete() {
            entryTokenRepository.setIfAbsent(1L, "correct-token", Duration.ofSeconds(60));

            boolean result = entryTokenRepository.validateAndDelete(1L, "correct-token");

            assertThat(result).isTrue();
            assertThat(entryTokenRepository.get(1L)).isNull();
        }

        @Test
        @DisplayName("잘못된 토큰이면 false를 반환하고 삭제하지 않는다")
        void validateAndDelete_WrongToken_ShouldReturnFalseAndKeep() {
            entryTokenRepository.setIfAbsent(1L, "correct-token", Duration.ofSeconds(60));

            boolean result = entryTokenRepository.validateAndDelete(1L, "wrong-token");

            assertThat(result).isFalse();
            assertThat(entryTokenRepository.get(1L)).isEqualTo("correct-token");
        }

        @Test
        @DisplayName("토큰이 없으면 false를 반환한다")
        void validateAndDelete_NotExists_ShouldReturnFalse() {
            boolean result = entryTokenRepository.validateAndDelete(99L, "any-token");
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("동시 2건 검증 시 정확히 1건만 성공한다 (Lua 원자성)")
        void validateAndDelete_Concurrent_ShouldSucceedOnlyOnce() throws Exception {
            Long userId = 42L;
            String token = "test-token-uuid";
            entryTokenRepository.setIfAbsent(userId, token, Duration.ofSeconds(60));

            int threadCount = 2;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(1);
            AtomicInteger successCount = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        latch.await();
                        boolean result = entryTokenRepository.validateAndDelete(userId, token);
                        if (result) successCount.incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
            latch.countDown();
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);

            assertThat(successCount.get()).isEqualTo(1);
            assertThat(entryTokenRepository.get(userId)).isNull();
        }
    }
}
