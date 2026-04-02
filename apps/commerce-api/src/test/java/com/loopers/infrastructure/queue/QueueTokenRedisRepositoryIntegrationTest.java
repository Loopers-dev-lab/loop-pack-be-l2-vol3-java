package com.loopers.infrastructure.queue;

import com.loopers.domain.queue.QueueConstants;
import com.loopers.domain.queue.QueueTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.loopers.config.redis.RedisConfig.REDIS_TEMPLATE_MASTER;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * [통합 테스트 - Redis Integration]
 *
 * 테스트 대상: QueueTokenRedisRepository
 * 테스트 유형: 통합 테스트 (Testcontainers Redis)
 * 테스트 범위: Repository -> Redis
 */
@SpringBootTest
@DisplayName("QueueTokenRedisRepository 통합 테스트")
class QueueTokenRedisRepositoryIntegrationTest {

    @Autowired
    private QueueTokenRepository queueTokenRepository;

    @Autowired
    @Qualifier(REDIS_TEMPLATE_MASTER)
    private RedisTemplate<String, String> redisTemplate;

    @BeforeEach
    void setUp() {
        Set<String> keys = redisTemplate.keys(QueueConstants.TOKEN_KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Nested
    @DisplayName("토큰 발급 (issue)")
    class Issue {

        @Test
        @DisplayName("성공 - 토큰을 발급하면 true를 반환한다")
        void issue_success() {
            // given
            Long userId = 1L;
            String token = "test-token-uuid";

            // when
            boolean result = queueTokenRepository.issue(userId, token, 300);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("성공 - 이미 토큰이 있으면 false를 반환한다")
        void issue_duplicate_returns_false() {
            // given
            Long userId = 1L;
            queueTokenRepository.issue(userId, "first-token", 300);

            // when
            boolean result = queueTokenRepository.issue(userId, "second-token", 300);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("성공 - 중복 발급 시 기존 토큰 값이 유지된다")
        void issue_duplicate_keeps_original_token() {
            // given
            Long userId = 1L;
            queueTokenRepository.issue(userId, "first-token", 300);

            // when
            queueTokenRepository.issue(userId, "second-token", 300);

            // then
            Optional<String> token = queueTokenRepository.getToken(userId);
            assertThat(token).hasValue("first-token");
        }

        @Test
        @DisplayName("성공 - TTL이 설정된다")
        void issue_sets_ttl() {
            // given
            Long userId = 1L;
            queueTokenRepository.issue(userId, "test-token", 300);

            // when
            Long ttl = redisTemplate.getExpire(QueueConstants.TOKEN_KEY_PREFIX + userId, TimeUnit.SECONDS);

            // then
            assertThat(ttl).isNotNull();
            assertThat(ttl).isGreaterThan(290L); // 약간의 실행 시간 감안
            assertThat(ttl).isLessThanOrEqualTo(300L);
        }

        @Test
        @DisplayName("성공 - 짧은 TTL(1초)을 설정하면 만료 후 조회 불가")
        void issue_short_ttl_expires() throws InterruptedException {
            // given
            Long userId = 1L;
            queueTokenRepository.issue(userId, "short-lived-token", 1);

            // when
            Thread.sleep(1500); // 1.5초 대기

            // then
            Optional<String> token = queueTokenRepository.getToken(userId);
            assertThat(token).isEmpty();
        }

        @Test
        @DisplayName("성공 - 서로 다른 유저에게 각각 토큰을 발급할 수 있다")
        void issue_different_users() {
            // given & when
            boolean result1 = queueTokenRepository.issue(1L, "token-1", 300);
            boolean result2 = queueTokenRepository.issue(2L, "token-2", 300);

            // then
            assertThat(result1).isTrue();
            assertThat(result2).isTrue();
            assertThat(queueTokenRepository.getToken(1L)).hasValue("token-1");
            assertThat(queueTokenRepository.getToken(2L)).hasValue("token-2");
        }
    }

    @Nested
    @DisplayName("토큰 조회 (getToken)")
    class GetToken {

        @Test
        @DisplayName("성공 - 발급된 토큰을 조회한다")
        void getToken_exists() {
            // given
            queueTokenRepository.issue(1L, "my-token", 300);

            // when
            Optional<String> token = queueTokenRepository.getToken(1L);

            // then
            assertThat(token).hasValue("my-token");
        }

        @Test
        @DisplayName("성공 - 발급되지 않은 유저는 empty")
        void getToken_not_exists() {
            // given & when
            Optional<String> token = queueTokenRepository.getToken(999L);

            // then
            assertThat(token).isEmpty();
        }
    }

    @Nested
    @DisplayName("토큰 존재 확인 (hasToken)")
    class HasToken {

        @Test
        @DisplayName("토큰이 있으면 true")
        void hasToken_exists() {
            // given
            queueTokenRepository.issue(1L, "token", 300);

            // when & then
            assertThat(queueTokenRepository.hasToken(1L)).isTrue();
        }

        @Test
        @DisplayName("토큰이 없으면 false")
        void hasToken_not_exists() {
            // given & when & then
            assertThat(queueTokenRepository.hasToken(999L)).isFalse();
        }

        @Test
        @DisplayName("토큰 만료 후 false")
        void hasToken_expired() throws InterruptedException {
            // given
            queueTokenRepository.issue(1L, "token", 1);

            // when
            Thread.sleep(1500);

            // then
            assertThat(queueTokenRepository.hasToken(1L)).isFalse();
        }
    }

    @Nested
    @DisplayName("토큰 삭제 (delete)")
    class Delete {

        @Test
        @DisplayName("성공 - 토큰을 삭제하면 조회 불가")
        void delete_success() {
            // given
            queueTokenRepository.issue(1L, "token", 300);

            // when
            queueTokenRepository.delete(1L);

            // then
            assertThat(queueTokenRepository.getToken(1L)).isEmpty();
            assertThat(queueTokenRepository.hasToken(1L)).isFalse();
        }

        @Test
        @DisplayName("성공 - 없는 토큰을 삭제해도 예외가 발생하지 않는다")
        void delete_not_exists_no_exception() {
            // given & when & then (예외 없음)
            queueTokenRepository.delete(999L);
        }
    }

    @Nested
    @DisplayName("동시성 테스트")
    class Concurrency {

        @Test
        @DisplayName("성공 - 동일 유저에게 동시에 토큰 발급 시 하나만 성공한다")
        void concurrent_issue_same_user() throws InterruptedException {
            // given
            int threadCount = 10;
            Long userId = 1L;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            // when
            for (int i = 0; i < threadCount; i++) {
                String token = "token-" + i;
                executor.submit(() -> {
                    try {
                        if (queueTokenRepository.issue(userId, token, 300)) {
                            successCount.incrementAndGet();
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();
            executor.shutdown();

            // then (NX 보장 — 하나만 성공)
            assertThat(successCount.get()).isEqualTo(1);
            assertThat(queueTokenRepository.hasToken(userId)).isTrue();
        }

        @Test
        @DisplayName("성공 - 서로 다른 유저에게 동시에 토큰 발급 시 모두 성공한다")
        void concurrent_issue_different_users() throws InterruptedException {
            // given
            int userCount = 20;
            ExecutorService executor = Executors.newFixedThreadPool(userCount);
            CountDownLatch latch = new CountDownLatch(userCount);
            AtomicInteger successCount = new AtomicInteger(0);

            // when
            for (int i = 1; i <= userCount; i++) {
                long userId = i;
                executor.submit(() -> {
                    try {
                        if (queueTokenRepository.issue(userId, "token-" + userId, 300)) {
                            successCount.incrementAndGet();
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();
            executor.shutdown();

            // then
            assertThat(successCount.get()).isEqualTo(userCount);
        }
    }

    @Nested
    @DisplayName("전체 흐름 테스트")
    class FullFlow {

        @Test
        @DisplayName("발급 → 검증 → 삭제 전체 흐름이 정상 동작한다")
        void issue_validate_delete_flow() {
            // given
            Long userId = 1L;

            // when: 발급
            boolean issued = queueTokenRepository.issue(userId, "flow-token", 300);

            // then: 발급 성공
            assertThat(issued).isTrue();
            assertThat(queueTokenRepository.hasToken(userId)).isTrue();
            assertThat(queueTokenRepository.getToken(userId)).hasValue("flow-token");

            // when: 삭제
            queueTokenRepository.delete(userId);

            // then: 삭제 후 조회 불가
            assertThat(queueTokenRepository.hasToken(userId)).isFalse();
            assertThat(queueTokenRepository.getToken(userId)).isEmpty();
        }

        @Test
        @DisplayName("토큰 만료 후 재발급이 가능하다")
        void reissue_after_expiry() throws InterruptedException {
            // given
            Long userId = 1L;
            queueTokenRepository.issue(userId, "first-token", 1);

            // when: 만료 대기
            Thread.sleep(1500);

            // then: 재발급 성공
            boolean reissued = queueTokenRepository.issue(userId, "second-token", 300);
            assertThat(reissued).isTrue();
            assertThat(queueTokenRepository.getToken(userId)).hasValue("second-token");
        }
    }
}
