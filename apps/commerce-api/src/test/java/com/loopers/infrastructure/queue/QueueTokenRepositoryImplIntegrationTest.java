package com.loopers.infrastructure.queue;

import com.loopers.domain.queue.QueueTokenRepository;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})
@ActiveProfiles("test")
class QueueTokenRepositoryImplIntegrationTest {

    @Autowired
    private QueueTokenRepository queueTokenRepository;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @Nested
    @DisplayName("issueToken / getToken")
    class IssueAndGet {

        @Test
        @DisplayName("토큰을 발급하고 조회하면 동일한 토큰을 반환한다")
        void 토큰을_발급하고_조회하면_동일한_토큰을_반환한다() {
            // given
            String eventId = "event-1";
            Long userId = 1L;
            String token = "abc-token-123";

            // when
            queueTokenRepository.issueToken(eventId, userId, token, 60);
            Optional<String> result = queueTokenRepository.getToken(eventId, userId);

            // then
            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo(token);
        }

        @Test
        @DisplayName("존재하지 않는 토큰을 조회하면 empty를 반환한다")
        void 존재하지_않는_토큰을_조회하면_empty를_반환한다() {
            // when
            Optional<String> result = queueTokenRepository.getToken("event-1", 999L);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getTokenTtl")
    class GetTokenTtl {

        @Test
        @DisplayName("발급된 토큰의 TTL을 조회하면 양수를 반환한다")
        void 발급된_토큰의_TTL을_조회하면_양수를_반환한다() {
            // given
            String eventId = "event-1";
            Long userId = 1L;
            queueTokenRepository.issueToken(eventId, userId, "token-1", 60);

            // when
            long ttl = queueTokenRepository.getTokenTtl(eventId, userId);

            // then
            assertThat(ttl).isPositive();
            assertThat(ttl).isLessThanOrEqualTo(60);
        }

        @Test
        @DisplayName("존재하지 않는 키의 TTL을 조회하면 음수를 반환한다")
        void 존재하지_않는_키의_TTL을_조회하면_음수를_반환한다() {
            // when
            long ttl = queueTokenRepository.getTokenTtl("event-1", 999L);

            // then
            assertThat(ttl).isNegative();
        }
    }

    @Nested
    @DisplayName("removeToken")
    class RemoveToken {

        @Test
        @DisplayName("토큰을 제거하면 조회 시 empty를 반환한다")
        void 토큰을_제거하면_조회_시_empty를_반환한다() {
            // given
            String eventId = "event-1";
            Long userId = 1L;
            queueTokenRepository.issueToken(eventId, userId, "token-1", 60);

            // when
            queueTokenRepository.removeToken(eventId, userId);
            Optional<String> result = queueTokenRepository.getToken(eventId, userId);

            // then
            assertThat(result).isEmpty();
        }
    }
}
