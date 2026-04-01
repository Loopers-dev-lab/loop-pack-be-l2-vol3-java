package com.loopers.infrastructure.shared.entry;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;

import com.loopers.config.redis.RedisConfig;
import com.loopers.support.BaseIntegrationTest;
import com.loopers.support.entry.EntryTokenStore;

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
}
