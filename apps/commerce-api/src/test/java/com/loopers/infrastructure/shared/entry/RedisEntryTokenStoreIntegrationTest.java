package com.loopers.infrastructure.shared.entry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;

import com.loopers.config.redis.RedisConfig;
import com.loopers.support.BaseIntegrationTest;
import com.loopers.support.entry.EntryTokenStore;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

@DisplayName("RedisEntryTokenStore 통합 테스트")
class RedisEntryTokenStoreIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private EntryTokenStore entryTokenStore;

    @Autowired
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private RedisTemplate<String, String> redisTemplate;

    @DisplayName("토큰을 조회할 때,")
    @Nested
    class GetToken {

        @DisplayName("토큰이 존재하면, 토큰 값을 반환한다.")
        @Test
        void returnsToken_whenExists() {
            // arrange
            redisTemplate.opsForValue().set("entry-token:1", "test-uuid-token");

            // act
            Optional<String> result = entryTokenStore.getToken(1L);

            // assert
            assertThat(result).isPresent().hasValue("test-uuid-token");
        }

        @DisplayName("토큰이 존재하지 않으면, 빈 Optional을 반환한다.")
        @Test
        void returnsEmpty_whenNotExists() {
            // act
            Optional<String> result = entryTokenStore.getToken(999L);

            // assert
            assertThat(result).isEmpty();
        }
    }

    @DisplayName("토큰을 검증할 때,")
    @Nested
    class Validate {

        @DisplayName("유효한 토큰이면, 예외 없이 통과하고 토큰은 유지된다.")
        @Test
        void passesValidation_whenValid() {
            // arrange
            Long userId = 1L;
            String token = "valid-token";
            redisTemplate.opsForValue().set("entry-token:" + userId, token);

            // act
            assertThatCode(() -> entryTokenStore.validate(userId, token))
                    .doesNotThrowAnyException();

            // assert
            assertThat(redisTemplate.opsForValue().get("entry-token:" + userId)).isEqualTo(token);
        }

        @DisplayName("토큰이 불일치하면, INVALID_ENTRY_TOKEN 예외가 발생한다.")
        @Test
        void throwsException_whenTokenMismatch() {
            // arrange
            Long userId = 2L;
            redisTemplate.opsForValue().set("entry-token:" + userId, "stored-token");

            // act & assert
            assertThatThrownBy(() -> entryTokenStore.validate(userId, "wrong-token"))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(ErrorType.INVALID_ENTRY_TOKEN);
        }

        @DisplayName("토큰이 존재하지 않으면, INVALID_ENTRY_TOKEN 예외가 발생한다.")
        @Test
        void throwsException_whenNoTokenExists() {
            // act & assert
            assertThatThrownBy(() -> entryTokenStore.validate(999L, "any-token"))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(ErrorType.INVALID_ENTRY_TOKEN);
        }
    }

    @DisplayName("토큰을 삭제할 때,")
    @Nested
    class Delete {

        @DisplayName("토큰이 존재하면, 삭제된다.")
        @Test
        void deletesToken_whenExists() {
            // arrange
            Long userId = 1L;
            redisTemplate.opsForValue().set("entry-token:" + userId, "token-to-delete");

            // act
            entryTokenStore.delete(userId);

            // assert
            assertThat(redisTemplate.opsForValue().get("entry-token:" + userId)).isNull();
        }

        @DisplayName("토큰이 존재하지 않아도, 예외 없이 성공한다.")
        @Test
        void doesNotThrow_whenNoTokenExists() {
            // act & assert
            assertThatCode(() -> entryTokenStore.delete(999L))
                    .doesNotThrowAnyException();
        }
    }

    @DisplayName("토큰 TTL이 만료될 때,")
    @Nested
    class TokenExpiration {

        @DisplayName("TTL이 지나면, 토큰이 조회되지 않는다.")
        @Test
        void returnsEmpty_whenTtlExpired() {
            // arrange
            redisTemplate.opsForValue().set("entry-token:1", "test-token", Duration.ofSeconds(1));

            // act & assert
            await().atMost(3, TimeUnit.SECONDS).untilAsserted(() ->
                    assertThat(entryTokenStore.getToken(1L)).isEmpty()
            );
        }
    }
}
