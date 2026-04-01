package com.loopers.domain.queue;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class EntryTokenServiceTest {

    @Autowired
    private EntryTokenService entryTokenService;

    @Autowired
    @Qualifier("redisTemplateMaster")
    private RedisTemplate<String, String> masterRedisTemplate;

    @BeforeEach
    void setUp() {
        // 테스트 전 토큰 초기화
        masterRedisTemplate.delete("entry-token:1");
    }

    @Nested
    @DisplayName("토큰 발급")
    class IssueToken {

        @DisplayName("토큰 발급 후 조회하면 값이 존재한다")
        @Test
        void issueAndGet() {
            String token = entryTokenService.issueToken(1L);

            assertThat(token).isNotNull();
            assertThat(entryTokenService.getToken(1L)).isEqualTo(token);
        }
    }

    @Nested
    @DisplayName("토큰 검증")
    class ValidateToken {

        @DisplayName("토큰 검증 후 삭제되어 재조회 시 null이다")
        @Test
        void validateDeletesToken() {
            entryTokenService.issueToken(1L);

            entryTokenService.validateAndDelete(1L);

            assertThat(entryTokenService.getToken(1L)).isNull();
        }

        @DisplayName("토큰이 없는 상태에서 검증하면 예외가 발생한다")
        @Test
        void validateWithoutTokenThrows() {
            assertThatThrownBy(() -> entryTokenService.validateAndDelete(1L))
                    .isInstanceOf(CoreException.class);
        }
    }

    @Nested
    @DisplayName("토큰 TTL")
    class TokenTTL {

        @DisplayName("TTL 초과 시 토큰이 만료된다")
        @Test
        void tokenExpiresAfterTTL() throws InterruptedException {
            // QueueProperties의 tokenTtlSeconds가 300이므로
            // 직접 짧은 TTL로 테스트하려면 Redis SET을 직접 호출해야 한다.
            // 또는 테스트용 TTL을 1초로 설정하는 별도 프로파일을 사용한다.

            // 직접 Redis에 1초 TTL로 SET
            masterRedisTemplate.opsForValue().set(
                    "entry-token:1", "test-token",
                    java.time.Duration.ofSeconds(1)
            );

            assertThat(entryTokenService.getToken(1L)).isNotNull();

            Thread.sleep(1500);

            assertThat(entryTokenService.getToken(1L)).isNull();
        }
    }
}
